/*
    Copyright 2010, Strategic Gains, Inc.

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.restexpress.ContentType;
import org.restexpress.Format;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.exception.BadRequestException;
import org.restexpress.response.DefaultHttpResponseWriter;
import org.restexpress.response.JsendResponseWrapper;
import org.restexpress.response.RawResponseWrapper;
import org.restexpress.response.StringBufferHttpResponseWriter;
import org.restexpress.route.RouteDeclaration;
import org.restexpress.route.RouteResolver;
import org.restexpress.serialization.NullSerializationProvider;
import org.restexpress.serialization.SerializationProvider;
import org.restexpress.serialization.json.JacksonJsonProcessor;
import org.restexpress.settings.RouteDefaults;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;


/**
 * @author toddf
 * @since Dec 15, 2010
 */
public class DefaultRequestHandlerTest
{
	private DefaultRequestHandler messageHandler;
	private DummyObserver observer;
	private Channel channel;
    private ChannelPipeline pl;
    private StringBuffer responseBody;
    private Map<String, List<String>> responseHeaders;
	
	@Before
	public void initialize()
	throws Exception
	{
		SerializationProvider provider = new NullSerializationProvider();
		provider.add(new JacksonJsonProcessor(Format.JSON), new RawResponseWrapper());
		provider.add(new JacksonJsonProcessor(Format.WRAPPED_JSON), new JsendResponseWrapper());
		provider.alias("dated", Dated.class);
		provider.setDefaultFormat(Format.WRAPPED_JSON);
		
		DummyRoutes routes = new DummyRoutes();
		routes.defineRoutes();
		messageHandler = new DefaultRequestHandler(new RouteResolver(routes.createRouteMapping(new RouteDefaults())), provider, new DefaultHttpResponseWriter(), false);
		observer = new DummyObserver();
		messageHandler.addMessageObserver(observer);
		responseBody = new StringBuffer();
		responseHeaders = new HashMap<String, List<String>>();
		messageHandler.setResponseWriter(new StringBufferHttpResponseWriter(responseHeaders, responseBody));
        channel = new EmbeddedChannel(messageHandler);
        pl = channel.pipeline();
	}

	@Test
	public void shouldReturnTextPlainContentTypeByDefault()
	throws Exception
	{
		sendGetEvent("/unserializedDefault");
		assertEquals(0, observer.getExceptionCount());
		assertEquals(1, observer.getReceivedCount());
		assertEquals(1, observer.getCompleteCount());
		assertEquals(1, observer.getSuccessCount());
//		System.out.println(responseBody.toString());
		assertEquals("should be text plain, here", responseBody.toString());
		assertTrue(responseHeaders.containsKey(HttpHeaderNames.CONTENT_TYPE.toString()));
		List<String> contentTypes = responseHeaders.get(HttpHeaderNames.CONTENT_TYPE.toString());
		assertEquals(1, contentTypes.size());
		assertEquals("text/plain; charset=UTF-8", contentTypes.get(0));
	}

	@Test
	public void shouldAllowSettingOfContentType()
	throws Exception
	{
		sendGetEvent("/unserialized");
		assertEquals(0, observer.getExceptionCount());
		assertEquals(1, observer.getReceivedCount());
		assertEquals(1, observer.getCompleteCount());
		assertEquals(1, observer.getSuccessCount());
//		System.out.println(responseBody.toString());
		assertEquals("<html><body>Some kinda wonderful!</body></html>", responseBody.toString());
		assertTrue(responseHeaders.containsKey(HttpHeaderNames.CONTENT_TYPE.toString()));
		List<String> contentTypes = responseHeaders.get(HttpHeaderNames.CONTENT_TYPE.toString());
		assertEquals(1, contentTypes.size());
		assertEquals("text/html", contentTypes.get(0));
	}

	@Test
	public void shouldAllowSettingOfContentTypeViaHeader()
	throws Exception
	{
		sendGetEvent("/unserializedToo");
		assertEquals(0, observer.getExceptionCount());
		assertEquals(1, observer.getReceivedCount());
		assertEquals(1, observer.getCompleteCount());
		assertEquals(1, observer.getSuccessCount());
//		System.out.println(responseBody.toString());
		assertEquals("<html><body>Wow! What a fabulous HTML body...</body></html>", responseBody.toString());
		List<String> contentTypes = responseHeaders.get(HttpHeaderNames.CONTENT_TYPE.toString());
		assertEquals(1, contentTypes.size());
		assertEquals("text/html", contentTypes.get(0));
	}

	@Test
	public void shouldAllowSettingOfArbitraryBody()
	throws Exception
	{
		sendGetEvent("/setBodyAction.html");
		assertEquals(0, observer.getExceptionCount());
		assertEquals(1, observer.getReceivedCount());
		assertEquals(1, observer.getCompleteCount());
		assertEquals(1, observer.getSuccessCount());
//		System.out.println(responseBody.toString());
		assertEquals("<html><body>Arbitrarily set HTML body...</body></html>", responseBody.toString());
		List<String> contentTypes = responseHeaders.get(HttpHeaderNames.CONTENT_TYPE.toString());
		assertEquals(1, contentTypes.size());
		assertEquals(ContentType.HTML, contentTypes.get(0));
	}

	@Test
	public void shouldNotifyObserverOnSuccess()
	throws Exception
	{
		sendGetEvent("/foo");
		assertEquals(1, observer.getReceivedCount());
		assertEquals(1, observer.getCompleteCount());
		assertEquals(1, observer.getSuccessCount());
		assertEquals(0, observer.getExceptionCount());
//		System.out.println(httpResponse.toString());
		assertEquals("{\"code\":200,\"status\":\"success\"}", responseBody.toString());
	}

	@Test
	public void shouldUrlDecodeUrlParameters()
	throws Exception
	{
		sendGetEvent("/foo/Todd%7CFredrich%2Bwas%20here.json");
		assertEquals(1, observer.getReceivedCount());
		assertEquals(1, observer.getCompleteCount());
		assertEquals(1, observer.getSuccessCount());
		assertEquals(0, observer.getExceptionCount());
//		System.out.println(httpResponse.toString());
		assertEquals("\"Todd|Fredrich+was here\"", responseBody.toString());
	}

	@Test
	public void shouldNotifyObserverOnError()
	throws Exception
	{
		sendGetEvent("/bar");
		assertEquals(1, observer.getReceivedCount());
		assertEquals(1, observer.getCompleteCount());
		assertEquals(1, observer.getExceptionCount());
		assertEquals(0, observer.getSuccessCount());
//		System.out.println(httpResponse.toString());
		assertEquals("{\"code\":400,\"status\":\"error\",\"message\":\"foobar'd\",\"data\":\"BadRequestException\"}", responseBody.toString());
	}

	@Test
	public void shouldHandleNonDecodableValueInQueryString()
	throws Exception
	{
		sendGetEvent("/bar?value=%target");
		assertEquals(1, observer.getReceivedCount());
		assertEquals(1, observer.getCompleteCount());
		assertEquals(1, observer.getExceptionCount());
		assertEquals(0, observer.getSuccessCount());
//		System.out.println(responseBody.toString());
		assertEquals("{\"code\":400,\"status\":\"error\",\"message\":\"foobar'd\",\"data\":\"BadRequestException\"}", responseBody.toString());
	}

	@Test
	public void shouldHandleUrlDecodeErrorInFormat()
	throws Exception
	{
		sendGetEvent("/foo.%target");
		assertEquals(1, observer.getReceivedCount());
		assertEquals(1, observer.getCompleteCount());
		assertEquals(1, observer.getExceptionCount());
		assertEquals(0, observer.getSuccessCount());
//		System.out.println(httpResponse.toString());
		String json = responseBody.toString();
		assertTrue(json.startsWith("{\"code\":400,\"status\":\"error\",\"message\":\"Requested representation format not supported: %target. Supported formats: "));
		assertTrue(json.contains("json"));
		assertTrue(json.contains("wjson"));
		assertTrue(json.endsWith("\",\"data\":\"BadRequestException\"}"));
	}

	@Test
	public void shouldThrowExceptionForErrorInFormat()
	throws Exception
	{
		sendGetEvent("/foo.unsupported");
		assertEquals(1, observer.getReceivedCount());
		assertEquals(1, observer.getCompleteCount());
		assertEquals(1, observer.getExceptionCount());
		assertEquals(0, observer.getSuccessCount());
//		System.out.println(httpResponse.toString());
		String json = responseBody.toString();
		assertTrue(json.startsWith("{\"code\":400,\"status\":\"error\",\"message\":\"Requested representation format not supported: unsupported. Supported formats: "));
		assertTrue(json.contains("json"));
		assertTrue(json.contains("wjson"));
		assertTrue(json.endsWith("\",\"data\":\"BadRequestException\"}"));
	}

	@Test
	public void shouldHandleInvalidDecodeInQueryString()
	throws Exception
	{
		sendGetEvent("/foo?value=%target");
		assertEquals(1, observer.getReceivedCount());
		assertEquals(1, observer.getCompleteCount());
		assertEquals(1, observer.getSuccessCount());
		assertEquals(0, observer.getExceptionCount());
//		System.out.println(httpResponse.toString());
		assertEquals("{\"code\":200,\"status\":\"success\"}", responseBody.toString());
	}

	@Test
	public void shouldHandleUrlDecodeErrorInUrl()
	throws Exception
	{
		sendGetEvent("/%bar");
		assertEquals(1, observer.getReceivedCount());
		assertEquals(1, observer.getCompleteCount());
		assertEquals(1, observer.getExceptionCount());
		assertEquals(0, observer.getSuccessCount());
//		System.out.println(httpResponse.toString());
		assertEquals("{\"code\":404,\"status\":\"error\",\"message\":\"Unresolvable URL: http://null/%bar\",\"data\":\"NotFoundException\"}", responseBody.toString());
	}

	@Test
	public void shouldParseTimepointJson()
	{
		sendGetEvent("/date.wjson", "{\"at\":\"2010-12-17T120000Z\"}");
		assertEquals(1, observer.getReceivedCount());
		assertEquals(1, observer.getCompleteCount());
		assertEquals(1, observer.getSuccessCount());
		assertEquals(0, observer.getExceptionCount());
//		System.out.println(httpResponse.toString());
		assertEquals("{\"code\":200,\"status\":\"success\",\"data\":{\"at\":\"2010-12-17T12:00:00.000Z\"}}", responseBody.toString());
	}

	@Test
	public void shouldParseTimepointJsonUsingQueryString()
	{
		sendGetEvent("/date?format=wjson", "{\"at\":\"2010-12-17T120000Z\"}");
		assertEquals(1, observer.getReceivedCount());
		assertEquals(1, observer.getCompleteCount());
		assertEquals(1, observer.getSuccessCount());
		assertEquals(0, observer.getExceptionCount());
//		System.out.println(httpResponse.toString());
		assertEquals("{\"code\":200,\"status\":\"success\",\"data\":{\"at\":\"2010-12-17T12:00:00.000Z\"}}", responseBody.toString());
	}

	@Test
	public void shouldCallAllFinallyProcessors()
	{
		NoopPostprocessor p1 = new NoopPostprocessor();
		NoopPostprocessor p2 = new NoopPostprocessor();
		NoopPostprocessor p3 = new NoopPostprocessor();
		messageHandler.addPostprocessor(p1);
		messageHandler.addPostprocessor(p2);
		messageHandler.addPostprocessor(p3);
		messageHandler.addFinallyProcessor(p1);
		messageHandler.addFinallyProcessor(p2);
		messageHandler.addFinallyProcessor(p3);
		sendGetEvent("/foo");
		assertEquals(2, p1.getCallCount());
		assertEquals(2, p2.getCallCount());
		assertEquals(2, p3.getCallCount());
	}

	@Test
	public void shouldCallAllFinallyProcessorsOnRouteException()
	{
		NoopPostprocessor p1 = new NoopPostprocessor();
		NoopPostprocessor p2 = new NoopPostprocessor();
		NoopPostprocessor p3 = new NoopPostprocessor();
		messageHandler.addPostprocessor(p1);
		messageHandler.addPostprocessor(p2);
		messageHandler.addPostprocessor(p3);
		messageHandler.addFinallyProcessor(p1);
		messageHandler.addFinallyProcessor(p2);
		messageHandler.addFinallyProcessor(p3);
		sendGetEvent("/xyzt.html");
		assertEquals(1, p1.getCallCount());
		assertEquals(1, p2.getCallCount());
		assertEquals(1, p3.getCallCount());
	}

	@Test
	public void shouldCallAllFinallyProcessorsOnProcessorException()
	{
		NoopPostprocessor p1 = new ExceptionPostprocessor();
		NoopPostprocessor p2 = new ExceptionPostprocessor();
		NoopPostprocessor p3 = new ExceptionPostprocessor();
		messageHandler.addPostprocessor(p1);	// this one throws the exception in Postprocessors
		messageHandler.addPostprocessor(p2);	// not called
		messageHandler.addPostprocessor(p3);	// not called
		messageHandler.addFinallyProcessor(p1);
		messageHandler.addFinallyProcessor(p2);
		messageHandler.addFinallyProcessor(p3);
		sendGetEvent("/foo");
		assertEquals(2, p1.getCallCount());
		assertEquals(1, p2.getCallCount());
		assertEquals(1, p3.getCallCount());
	}

	@Test
	public void shouldSetJSONContentType()
	throws Exception
	{
		sendGetEvent("/serializedString.json?returnValue=raw+string");
		assertEquals(0, observer.getExceptionCount());
		assertEquals(1, observer.getReceivedCount());
		assertEquals(1, observer.getCompleteCount());
		assertEquals(1, observer.getSuccessCount());
		assertEquals("\"raw string\"", responseBody.toString());
		assertTrue(responseHeaders.containsKey(HttpHeaderNames.CONTENT_TYPE.toString()));
		List<String> contentTypes = responseHeaders.get(HttpHeaderNames.CONTENT_TYPE.toString());
		assertEquals(1, contentTypes.size());
		assertEquals("application/json; charset=UTF-8", contentTypes.get(0));
	}

	@Test
	public void shouldSetJSONContentTypeOnNullReturn()
	throws Exception
	{
		sendGetEvent("/serializedString.json");
		assertEquals(0, observer.getExceptionCount());
		assertEquals(1, observer.getReceivedCount());
		assertEquals(1, observer.getCompleteCount());
		assertEquals(1, observer.getSuccessCount());
		assertTrue(responseHeaders.containsKey(HttpHeaderNames.CONTENT_TYPE.toString()));
		List<String> contentTypes = responseHeaders.get(HttpHeaderNames.CONTENT_TYPE.toString());
		assertEquals(1, contentTypes.size());
		assertEquals("application/json; charset=UTF-8", contentTypes.get(0));
		assertEquals("null", responseBody.toString());
	}

	private void sendGetEvent(String path)
    {
		try
		{
		    pl.fireChannelRead(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, path));
		}
		catch(Throwable t)
		{
			System.out.println(t.getMessage());
			t.printStackTrace();
		}
    }

	private void sendGetEvent(String path, String body)
    {
		try
		{
		    pl.fireChannelRead(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, path, Unpooled.copiedBuffer(body, Charset.defaultCharset())));
		}
		catch(Throwable t)
		{
			System.out.println(t.getMessage());
		}
    }
	
	public class DummyRoutes
	extends RouteDeclaration
	{
		private Object controller = new FooBarController();
		private RouteDefaults defaults = new RouteDefaults();

        public void defineRoutes()
        {
        	uri("/foo.{format}", controller, defaults)
        		.action("fooAction", HttpMethod.GET);

        	uri("/foo/{userPhrase}.{format}", controller, defaults)
    			.action("verifyUrlDecodedParameters", HttpMethod.GET);

        	uri("/bar.{format}", controller, defaults)
        		.action("barAction", HttpMethod.GET);

        	uri("/date.{format}", controller, defaults)
    			.action("dateAction", HttpMethod.GET);

        	uri("/unserializedDefault", controller, defaults)
        		.action("unserializedDefault", HttpMethod.GET);

        	uri("/unserialized", controller, defaults)
        		.action("unserializedAction", HttpMethod.GET);

        	uri("/unserializedToo", controller, defaults)
        		.action("contentHeaderAction", HttpMethod.GET);

        	uri("/serializedString.{format}", controller, defaults)
    		.action("serializedStringAction", HttpMethod.GET);

        	uri("/setBodyAction.html", controller, defaults)
        		.action("setBodyAction", HttpMethod.GET)
        		.format(Format.HTML);
        }
	}
	
	public class FooBarController
	{
		public void fooAction(Request request, Response response)
		{
			// do nothing.
		}
		
		public String verifyUrlDecodedParameters(Request request, Response response)
		{
			return request.getHeader("userPhrase");
		}
		
		public void barAction(Request request, Response response)
		{
			throw new BadRequestException("foobar'd");
		}

		public Object dateAction(Request request, Response response)
		{
			return request.getBodyAs(Dated.class);
		}

		public String unserializedDefault(Request request, Response response)
		{
			response.noSerialization();
			return "should be text plain, here";
		}

		public String unserializedAction(Request request, Response response)
		{
			response.setContentType("text/html");
			response.noSerialization();
			return "<html><body>Some kinda wonderful!</body></html>";
		}

		public String serializedStringAction(Request request, Response response)
		{
			return request.getHeader("returnValue");
		}

		public String contentHeaderAction(Request request, Response response)
		{
			response.addHeader(HttpHeaderNames.CONTENT_TYPE.toString(), "text/html");
			response.noSerialization();
			return "<html><body>Wow! What a fabulous HTML body...</body></html>";
		}

		public void setBodyAction(Request request, Response response)
		{
			response.setContentType(ContentType.HTML);
			response.noSerialization();
			response.setBody("<html><body>Arbitrarily set HTML body...</body></html>");
		}
	}

	public class DummyObserver
	extends MessageObserver
	{
		private int receivedCount = 0;
		private int exceptionCount = 0;
		private int successCount = 0;
		private int completeCount = 0;

		@Override
        protected void onReceived(Request request, Response response)
        {
			++receivedCount;
        }

		@Override
        protected void onException(Throwable exception, Request request, Response response)
        {
			++exceptionCount;
        }

		@Override
        protected void onSuccess(Request request, Response response)
        {
			++successCount;
        }

		@Override
        protected void onComplete(Request request, Response response)
        {
			++completeCount;
        }

		public int getReceivedCount()
        {
        	return receivedCount;
        }

		public int getExceptionCount()
        {
        	return exceptionCount;
        }

		public int getSuccessCount()
        {
        	return successCount;
        }

		public int getCompleteCount()
        {
        	return completeCount;
        }
	}

	private class NoopPostprocessor
	implements Postprocessor
	{
		private int callCount = 0;

        @Override
        public void process(Request request, Response response)
        {
        	++callCount;
        }
        
        public int getCallCount()
        {
        	return callCount;
        }
	}

	private class ExceptionPostprocessor
	extends NoopPostprocessor
	{
        @Override
        public void process(Request request, Response response)
        {
        	super.process(request, response);
        	throw new RuntimeException("RuntimeException thrown...");
        }
	}
}
