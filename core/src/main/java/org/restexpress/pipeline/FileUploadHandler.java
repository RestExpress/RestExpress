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
import org.restexpress.exception.ServiceException;
import org.restexpress.response.HttpResponseWriter;
import org.restexpress.route.Action;
import org.restexpress.route.RouteResolver;
import org.restexpress.serialization.DefaultSerializationProvider;
import org.restexpress.serialization.SerializationProvider;
import org.restexpress.serialization.SerializationSettings;
import org.restexpress.util.HttpSpecification;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder.EndOfDataDecoderException;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.codec.http.multipart.InterfaceHttpData.HttpDataType;
import io.netty.util.AttributeKey;

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
@Sharable
public class FileUploadHandler
extends SimpleChannelInboundHandler<HttpObject>
{

	private static final AttributeKey<MessageContext> CONTEXT_KEY = AttributeKey.valueOf("context");
	public static final String FILE_ATTACHMENT_KEY = "filePath";
	private final RouteResolver routeResolver;
	private final SerializationProvider serializationProvider;
	private HttpResponseWriter responseWriter;
	private static final AttributeKey<UploadState> UPLOAD_KEY = AttributeKey.valueOf(FileUploadHandler.class, "upload");
	private static final HttpDataFactory factory = new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE);
	private boolean shouldEnforceHttpSpec = true;
	private static SerializationProvider DEFAULT_SERIALIZATION_PROVIDER = null;

	// A handler is shared across connections; upload resources belong to one channel.
	private static class UploadState
	{
		private HttpPostRequestDecoder decoder;
		private File filePath;
		private FullHttpRequest fullHttpRequest;
		private MessageContext messageContext;
	}
	private ExceptionMapping exceptionMap = new DefaultExceptionMapper();

	public FileUploadHandler(RouteResolver routeResolver, SerializationProvider serializationProvider, HttpResponseWriter responseWriter, boolean enforceHttpSpec)
	{
		super();
		this.routeResolver = routeResolver;
		this.serializationProvider = serializationProvider;
		setResponseWriter(responseWriter);
		this.shouldEnforceHttpSpec = enforceHttpSpec;
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
			try
			{
				reset(channelHandlerContext);
			}
			catch (Exception cleanupFailure)
			{
				e.addSuppressed(cleanupFailure);
			}
			channelHandlerContext.fireExceptionCaught(e);
			channelHandlerContext.close();
		}
	}

	private void processRequest(ChannelHandlerContext ctx, HttpObject object)
	{
		UploadState state = ctx.attr(UPLOAD_KEY).get();

		if (object instanceof HttpRequest)
		{
			reset(ctx);
			state = new UploadState();
			ctx.attr(UPLOAD_KEY).set(state);
			HttpRequest request = (HttpRequest) object;
			state.fullHttpRequest = new DefaultFullHttpRequest(request.protocolVersion(), request.method(), request.uri());
			state.fullHttpRequest.headers().set(request.headers());
			state.filePath = Paths.get(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString()).toFile();
			state.messageContext = createInitialContext(ctx, state.fullHttpRequest);
			state.decoder = new HttpPostRequestDecoder(factory, request);
		}

		if (object instanceof HttpContent)
		{
			if (state == null || state.decoder == null)
			{
				throw new IllegalStateException("Upload content received without a request");
			}
			state.decoder.offer((HttpContent) object);
			readChunk(state);

			if (object instanceof LastHttpContent)
			{
				try
				{
					prepareResponse(ctx, state);
				}
				finally
				{
					reset(ctx);
				}
			}
		}
	}

	private void prepareResponse(ChannelHandlerContext channelHandlerContext, UploadState state)
	{
		resolveRoute(state.messageContext);
		resolveResponseProcessor(state.messageContext);
		state.messageContext.getRequest().putAttachment(FILE_ATTACHMENT_KEY, state.filePath.getAbsolutePath());
		Object result = state.messageContext.getAction().invoke(state.messageContext.getRequest(), state.messageContext.getResponse());

		if (result != null)
		{
			state.messageContext.getResponse().setBody(result);
		}

		serializeResponse(state.messageContext, false);
		enforceHttpSpecification(state.messageContext);
		writeResponse(channelHandlerContext, state.messageContext);
	}

	private void reset(ChannelHandlerContext ctx)
	{
		UploadState state = ctx.attr(UPLOAD_KEY).getAndSet(null);
		if (state == null) return;
		ctx.attr(CONTEXT_KEY).set(null);
		try
		{
			if (state.decoder != null) state.decoder.destroy();
		}
		finally
		{
			if (state.fullHttpRequest != null) state.fullHttpRequest.release();
		}
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception
	{
		try
		{
			reset(ctx);
		}
		finally
		{
			super.channelInactive(ctx);
		}
	}

	@Override
	public void handlerRemoved(ChannelHandlerContext ctx) throws Exception
	{
		reset(ctx);
		super.handlerRemoved(ctx);
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception
	{
		try
		{
			reset(ctx);
		}
		finally
		{
			ctx.fireExceptionCaught(cause);
			ctx.close();
		}
	}

	private void readChunk(UploadState state)
	{
		try
		{
			while (state.decoder.hasNext())
			{
				InterfaceHttpData data = state.decoder.next();
				if (data != null) writeData(data, state);
			}
		}
		catch (EndOfDataDecoderException e)
		{
			// Normal end of multipart input. The decoder owns and releases its data.
		}
	}

	private void writeData(InterfaceHttpData data, UploadState state)
	{
		if (data.getHttpDataType() == HttpDataType.FileUpload)
		{
			FileUpload fileUpload = (FileUpload) data;

			if (fileUpload.isCompleted())
			{
				try
				{
					if (!fileUpload.renameTo(state.filePath))
					{
						throw new IOException("Unable to move uploaded file");
					}
				}
				catch (IOException e)
				{
					throw new IllegalStateException("Unable to store multipart upload", e);
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
