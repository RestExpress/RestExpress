/*
    Copyright 2021, Strategic Gains, Inc.

	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at

		http://www.apache.org/licenses/LICENSE-2.0

	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.
 */
package org.restexpress.pipeline;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder.EndOfDataDecoderException;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder.ErrorDataDecoderException;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.codec.http.multipart.InterfaceHttpData.HttpDataType;
import io.netty.util.AttributeKey;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.UUID;
import org.restexpress.ContentType;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.exception.DefaultExceptionMapper;
import org.restexpress.exception.ExceptionMapping;
import org.restexpress.response.DefaultHttpResponseWriter;
import org.restexpress.response.HttpResponseWriter;
import org.restexpress.route.Action;
import org.restexpress.route.RouteResolver;
import org.restexpress.serialization.DefaultSerializationProvider;
import org.restexpress.serialization.SerializationProvider;
import org.restexpress.serialization.SerializationSettings;
import org.restexpress.util.HttpSpecification;

/***
 * @author - Murali S Rao
 *
 * An upload handler to handle large file uploads in Rest Express.
 * Involves adding a decoder to "divert" POST Multipart requests towards this handler
 * and pass the rest of messages to the Default Request Handler. (See RequestURLDecoder.java)
 *
 * The controller can access the file received by this handler
 * by retrieving the path of the file using an "attachment" named "FILE_ATTACHMENT_KEY"
 *
 */
public class FileUploadHandler
extends SimpleChannelInboundHandler<HttpObject>
{

	private static final AttributeKey<MessageContext> CONTEXT_KEY = AttributeKey.valueOf("context");
	public static final String FILE_ATTACHMENT_KEY = "filePath";
	private final RouteResolver routeResolver;
	private final SerializationProvider serializationProvider;
	private HttpResponseWriter responseWriter;
	private HttpPostRequestDecoder decoder;
	private static final HttpDataFactory factory = new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE);
	private File filePath;
	private boolean shouldEnforceHttpSpec = true;
	private static SerializationProvider DEFAULT_SERIALIZATION_PROVIDER = null;

	// Save a reference to request and the context since we accumulate chunks in between
	private FullHttpRequest fullHttpRequest = null;
	private MessageContext messageContext = null;
	private ExceptionMapping exceptionMap = new DefaultExceptionMapper();

	public FileUploadHandler(RouteResolver routeResolver)
	{
		super();
		this.routeResolver = routeResolver;
		this.serializationProvider = new DefaultSerializationProvider();
		setResponseWriter(new DefaultHttpResponseWriter());
		this.shouldEnforceHttpSpec = true;
	}

	@Override
	public void channelReadComplete(ChannelHandlerContext ctx) throws Exception
	{
		ctx.flush();
		super.channelReadComplete(ctx);
	}

	public void channelRead0(ChannelHandlerContext channelHandlerContext, HttpObject object)
	throws Exception
	{
		try
		{
			processRequest(channelHandlerContext, object);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	private void processRequest(ChannelHandlerContext channelHandlerContext, HttpObject object)
	{
		if (object instanceof HttpRequest)
		{
			HttpRequest request = (HttpRequest) object;
			fullHttpRequest = new DefaultFullHttpRequest(request.protocolVersion(), request.method(), request.uri());
			fullHttpRequest.headers().set(request.headers());
			String uuid = UUID.randomUUID().toString();
			filePath = Paths.get(System.getProperty("java.io.tmpdir"), uuid).toFile();
			messageContext = createInitialContext(channelHandlerContext, fullHttpRequest);

			try
			{
				decoder = new HttpPostRequestDecoder(factory, request);
			}
			catch (ErrorDataDecoderException e1)
			{
				e1.printStackTrace();
				channelHandlerContext.channel().close();
				return;
			}
		}

		if (object instanceof HttpContent)
		{
			HttpContent chunk = (HttpContent) object;
			decoder.offer(chunk);
			readChunk();

			if (object instanceof LastHttpContent)
			{
				prepareResponse(channelHandlerContext);
				reset();
			}
		}
	}

	private void prepareResponse(ChannelHandlerContext channelHandlerContext)
	{
		resolveRoute(messageContext);
		resolveResponseProcessor(messageContext);
		messageContext.getRequest().putAttachment(FILE_ATTACHMENT_KEY, filePath.getAbsolutePath());
		Object result = messageContext.getAction().invoke(messageContext.getRequest(), messageContext.getResponse());

		if (result != null)
		{
			messageContext.getResponse().setBody(result);
		}

		serializeResponse(messageContext, false);
		enforceHttpSpecification(messageContext);
		writeResponse(channelHandlerContext, messageContext);
	}

	private void reset()
	{
		// destroy the decoder to release all resources
		decoder.destroy();
		decoder = null;
		fullHttpRequest = null;
		messageContext = null;
	}

	private void readChunk()
	{
		try
		{
			while (decoder.hasNext())
			{
				InterfaceHttpData data = decoder.next();
				if (data != null)
				{
					try
					{
						writeData(data);
					}
					finally
					{
						data.release();
					}
				}
			}
		}
		catch (EndOfDataDecoderException e)
		{
			e.printStackTrace();
		}
	}

	private void writeData(InterfaceHttpData data)
	{
		if (data.getHttpDataType() == HttpDataType.Attribute)
		{
			Attribute attribute = (Attribute) data;
			String value;
			try
			{
				value = attribute.getValue();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}

		}
		else
		{
			FileUpload fileUpload = (FileUpload) data;

			if (fileUpload.isCompleted())
			{
				try
				{
					fileUpload.renameTo(filePath);
				}
				catch (IOException e)
				{
					e.printStackTrace();
				}
			}
		}
	}

	private MessageContext createInitialContext(ChannelHandlerContext ctx, FullHttpRequest httpRequest)
	{
		Request request = createRequest(httpRequest, ctx);
		Response response = createResponse();
		MessageContext context = new MessageContext(request, response);
		ctx.attr(CONTEXT_KEY).set(context);
		return context;
	}

	public HttpResponseWriter getResponseWriter()
	{
		return this.responseWriter;
	}

	public void setResponseWriter(HttpResponseWriter writer)
	{
		this.responseWriter = writer;
	}

	private Request createRequest(FullHttpRequest request, ChannelHandlerContext context)
	{
		try
		{
			return new Request((InetSocketAddress) context.channel().remoteAddress(), request,
			    routeResolver, serializationProvider);
		}
		catch (Throwable t)
		{
			return new Request(request, routeResolver, serializationProvider);
		}
	}

	private Response createResponse()
	{
		return new Response();
	}

	private void writeResponse(ChannelHandlerContext ctx, MessageContext context)
	{
		getResponseWriter().write(ctx, context.getRequest(), context.getResponse());
	}

	private void resolveResponseProcessor(MessageContext context)
	{
		SerializationSettings s = serializationProvider.resolveResponse(context.getRequest(), context.getResponse(), false);
		context.setSerializationSettings(s);
	}

	private void resolveRoute(MessageContext context)
	{
		Action action = routeResolver.resolve(context.getRequest());
		context.setAction(action);
	}

	private void enforceHttpSpecification(MessageContext context)
	{
		if (shouldEnforceHttpSpec)
		{
			HttpSpecification.enforce(context.getResponse());
		}
	}

	private void serializeResponse(MessageContext context, boolean force)
	{
		Response response = context.getResponse();

		if (HttpSpecification.isContentTypeAllowed(response))
		{
			SerializationSettings settings = null;

			if (response.hasSerializationSettings())
			{
				settings = response.getSerializationSettings();
			}
			else if (force)
			{
				settings = serializationProvider.resolveResponse(context.getRequest(), response, force);
			}

			if (settings != null)
			{
				if (response.isSerialized())
				{
					ByteBuffer serialized = settings.serialize(response);

					if (serialized != null)
					{
						response.setBody(Unpooled.wrappedBuffer(serialized));

						if (!response.hasHeader(HttpHeaders.Names.CONTENT_TYPE))
						{
							response.setContentType(settings.getMediaType());
						}
					}
				}
			}

			if (!response.hasHeader(HttpHeaders.Names.CONTENT_TYPE))
			{
				response.setContentType(ContentType.TEXT_PLAIN);
			}
		}
	}

	public FileUploadHandler setExceptionMap(ExceptionMapping map)
	{
		this.exceptionMap = map;
		return this;
	}

	public SerializationProvider getDefaultSerializationProvider()
	{
		if (DEFAULT_SERIALIZATION_PROVIDER == null)
		{
			DEFAULT_SERIALIZATION_PROVIDER = new DefaultSerializationProvider();
		}

		return DEFAULT_SERIALIZATION_PROVIDER;
	}
}
