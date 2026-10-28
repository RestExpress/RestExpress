package org.restexpress;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.restexpress.common.query.QueryRange;
import org.restexpress.postprocessor.TestPostprocessor;
import org.restexpress.preprocessor.ErrorPreprocessor;
import org.restexpress.response.ErrorResponseWrapper;
import org.restexpress.serialization.AbstractSerializationProvider;
import org.restexpress.serialization.NullSerializationProvider;
import org.restexpress.serialization.json.JacksonJsonProcessor;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;

public class AltRestExpressServerTest
{
	private static final int DEFAULT_PORT = 8801;
	private static final String SERVER_HOST = "http://localhost:" + DEFAULT_PORT;
	private static final String URL_PATTERN1 = "/1/restexpress/{id}/test/{test}.{format}";
	private static final String URL_PATTERN2 = "/2/restexpress/{id}/test/{test}";
	private static final String URL_PATTERN3 = "/3/restexpress/{id}/test/{test}.{format}";
	private static final String URL_PATTERN4 = "/4/restexpress/{id}/test/{test}.{format}";
	private static final String LITTLE_O_PATTERN = "/littleos/{id}.{format}";
	private static final String LITTLE_OS_PATTERN = "/littleos.{format}";
	private static final String URL_PATH1 = "/1/restexpress/sam/test/42";
	private static final String URL_PATH3 = "/3/restexpress/polly/test/56";
	private static final String URL_PATH4 = "/4/restexpress/allen/test/33";
	private static final String LITTLE_O_PATH = "/littleos/1";
	private static final String LITTLE_OS_PATH = "/littleos";
	private static final String URL1_PLAIN = SERVER_HOST + URL_PATH1;
	private static final String URL1_JSON = SERVER_HOST + URL_PATH1 + ".json";
	private static final String URL3_PLAIN = SERVER_HOST + URL_PATH3;
	private static final String URL4_PLAIN = SERVER_HOST + URL_PATH4;
	private static final String LITTLE_O_URL = SERVER_HOST + LITTLE_O_PATH;
	private static final String LITTLE_OS_URL = SERVER_HOST + LITTLE_OS_PATH;
	private static final String PATTERN_EXCEPTION_STRING = "/strings/exception";
	private static final String PATTERN_EXCEPTION_LITTLE_O = "/objects/exception";
	private static final String ECHO_PATTERN = "/echo";
	private static final String URL_EXCEPTION_LITTLE_O = SERVER_HOST + PATTERN_EXCEPTION_LITTLE_O;
	private static final String URL_ECHO = SERVER_HOST + ECHO_PATTERN;

	private static final CloseableHttpClient CLIENT = new DefaultHttpClient();
	private static final AbstractSerializationProvider DEFAULT_SERIALIZER = new NullSerializationProvider();

	static
	{
		JacksonJsonProcessor jackson = new JacksonJsonProcessor(Format.JSON);
		jackson.addSupportedMediaTypes(ContentType.HAL_JSON);
		DEFAULT_SERIALIZER.add(jackson, new ErrorResponseWrapper(), true);
	}

	private static RestExpress SERVER;
	private static final TestPostprocessor POSTPROCESSOR = new TestPostprocessor();
	private static final ErrorPreprocessor ERROR_PREPROCESSOR = new ErrorPreprocessor();

	public RestExpress createServer()
	{
		RestExpress server = new RestExpress();
		server.setReadTimeoutSeconds(1);
		RestExpress.setDefaultSerializationProvider(DEFAULT_SERIALIZER);
		StringTestController stringTestController = new StringTestController();
		ObjectTestController objectTestController = new ObjectTestController();
		EchoTestController echoTestController = new EchoTestController();
		server.addPreprocessor(ERROR_PREPROCESSOR);
		server.addFinallyProcessor(POSTPROCESSOR);

		server.uri(URL_PATTERN1, stringTestController);
		server.uri(URL_PATTERN2, stringTestController);
		server.uri(URL_PATTERN3, stringTestController)
		    .method(HttpMethod.GET, HttpMethod.POST)
		    .action("read", HttpMethod.HEAD);

		server.uri(PATTERN_EXCEPTION_STRING, stringTestController)
			.action("throwException", HttpMethod.GET);
		server.uri(URL_PATTERN4, stringTestController) // Collection route.
		    .method(HttpMethod.POST).action("readAll", HttpMethod.GET);
		server.uri(LITTLE_O_PATTERN, objectTestController)
			.method(HttpMethod.GET);
		server.uri(LITTLE_OS_PATTERN, objectTestController)
			.action("readAll", HttpMethod.GET);
		server.uri(PATTERN_EXCEPTION_LITTLE_O, objectTestController)
			.action("throwException", HttpMethod.GET);
		server.uri(ECHO_PATTERN, echoTestController)
			.action("update", HttpMethod.PUT);
		server.uri("/unserialized", new StringTestController())
			.noSerialization();

		server.alias("littleObject", LittleO.class);
		return server;
	}

	@Before
	public void ensureServerRunning()
	throws Throwable
	{
		POSTPROCESSOR.resetCallCount();
		ERROR_PREPROCESSOR.shouldThrow(false);

		if (SERVER == null)
		{
			SERVER = createServer();
			SERVER.bind(DEFAULT_PORT);

			Thread.sleep(500L);
		}
	}

	@After
	public void afterTest()
	{
		DEFAULT_SERIALIZER.setDefaultFormat(Format.JSON);
	}

	@AfterClass
	public static void shutdownServer()
	throws IOException
	{
		CLIENT.close();
		SERVER.shutdown(true);
	}

	// SECTION: TESTS

	@Test
	public void shouldHandleGetRequests() throws Exception
	{
		HttpGet request = new HttpGet(URL1_PLAIN);

		try
		{
			HttpResponse response = (HttpResponse) CLIENT.execute(request);
			assertEquals(HttpResponseStatus.OK.code(), response.getStatusLine().getStatusCode());
			HttpEntity entity = response.getEntity();
			assertTrue(entity.getContentLength() > 0l);
			assertEquals(ContentType.JSON, entity.getContentType().getValue());
			assertEquals("\"read\"", EntityUtils.toString(entity));
		}
		finally
		{
			request.releaseConnection();
		}
	}

	@Test
	public void shouldHandleHeadRequests() throws Exception
	{
		HttpHead request = new HttpHead(URL3_PLAIN);

		try
		{
			HttpResponse response = (HttpResponse) CLIENT.execute(request);
			assertEquals(HttpResponseStatus.OK.code(), response.getStatusLine().getStatusCode());
			assertEquals(String.valueOf("\"read\"".length()), response.getFirstHeader(HttpHeaderNames.CONTENT_LENGTH.toString().toString()).getValue());
			assertEquals(ContentType.JSON, response.getFirstHeader(HttpHeaderNames.CONTENT_TYPE.toString()).getValue());
			assertNull(response.getEntity());
		}
		finally
		{
			request.releaseConnection();
		}
	}

	@Test
	public void shouldHandlePutRequests() throws Exception
	{
		HttpPut request = new HttpPut(URL1_PLAIN);

		try
		{
			HttpResponse response = (HttpResponse) CLIENT.execute(request);
			assertEquals(HttpResponseStatus.OK.code(), response.getStatusLine().getStatusCode());
			HttpEntity entity = response.getEntity();
			assertTrue(entity.getContentLength() > 0l);
			assertEquals(ContentType.JSON, entity.getContentType().getValue());
			assertEquals("\"update\"", EntityUtils.toString(entity));
		}
		finally
		{
			request.releaseConnection();
		}
	}

	@Test
	public void shouldHandlePostRequests() throws Exception
	{
		HttpPost request = new HttpPost(URL1_PLAIN);

		try
		{
			HttpResponse response = (HttpResponse) CLIENT.execute(request);
			assertEquals(HttpResponseStatus.CREATED.code(), response.getStatusLine().getStatusCode());
			HttpEntity entity = response.getEntity();
			assertTrue(entity.getContentLength() > 0l);
			assertEquals(ContentType.JSON, entity.getContentType().getValue());
			assertEquals("\"create\"", EntityUtils.toString(entity));
		}
		finally
		{
			request.releaseConnection();
		}
	}

	@Test
	public void shouldHandleDeleteRequests() throws Exception
	{
		HttpDelete request = new HttpDelete(URL1_PLAIN);

		try
		{
			HttpResponse response = (HttpResponse) CLIENT.execute(request);
			assertEquals(HttpResponseStatus.OK.code(), response.getStatusLine().getStatusCode());
			HttpEntity entity = response.getEntity();
			assertTrue(entity.getContentLength() > 0l);
			assertEquals(ContentType.JSON, entity.getContentType().getValue());
			assertEquals("\"delete\"", EntityUtils.toString(entity));
		}
		finally
		{
			request.releaseConnection();
		}
	}

	@Test
	public void shouldCallSpecifiedMethod() throws Exception
	{
		HttpGet request = new HttpGet(URL4_PLAIN);

		try
		{
			HttpResponse response = (HttpResponse) CLIENT.execute(request);
			assertEquals(HttpResponseStatus.OK.code(), response.getStatusLine().getStatusCode());
			HttpEntity entity = response.getEntity();
			assertTrue(entity.getContentLength() > 0l);
			assertEquals(ContentType.JSON, entity.getContentType().getValue());
			assertEquals("\"readAll\"", EntityUtils.toString(entity));
		}
		finally
		{
			request.releaseConnection();
		}
	}

	@Test
	public void shouldFailWithMethodNotAllowed() throws Exception
	{
		HttpDelete request = new HttpDelete(URL3_PLAIN);

		try
		{
			HttpResponse response = (HttpResponse) CLIENT.execute(request);
			assertEquals(HttpResponseStatus.METHOD_NOT_ALLOWED.code(), response.getStatusLine().getStatusCode());
			HttpEntity entity = response.getEntity();
			assertTrue(entity.getContentLength() > 0l);
			assertEquals(ContentType.JSON, entity.getContentType().getValue());
			String json = EntityUtils.toString(entity);
			assertTrue(json.startsWith("{\"errorId\":"));
			assertTrue(json.contains("\"httpStatus\":405"));
			assertTrue(json.contains("\"message\":\"" + URL3_PLAIN + "\""));
			assertTrue(json.contains("\"errorType\":\"MethodNotAllowedException\""));
			String methods = response.getHeaders(HttpHeaderNames.ALLOW.toString())[0].getValue();
			assertTrue(methods.contains("GET"));
			assertTrue(methods.contains("POST"));
		}
		finally
		{
			request.releaseConnection();
		}
	}

	@Test
	public void shouldFailWithOk() throws Exception
	{
		HttpDelete request = new HttpDelete(URL3_PLAIN + "?"
		    + Parameters.Query.IGNORE_HTTP_STATUS + "=true");

		try
		{
			HttpResponse response = (HttpResponse) CLIENT.execute(request);
			assertEquals(HttpResponseStatus.OK.code(), response.getStatusLine().getStatusCode());
			HttpEntity entity = response.getEntity();
			assertTrue(entity.getContentLength() > 0l);
			assertEquals(ContentType.JSON, entity.getContentType().getValue());
			String json = EntityUtils.toString(entity);
			assertNotNull(json);
			assertTrue(json.startsWith("{\"errorId\":"));
			assertTrue(json.contains("\"httpStatus\":405"));
			assertTrue(json.contains("\"message\":\"" + URL3_PLAIN + "?_ignore_http_status=true\""));
			assertTrue(json.contains("\"errorType\":\"MethodNotAllowedException\""));
			String methods = response.getHeaders(HttpHeaderNames.ALLOW.toString())[0].getValue();
			assertTrue(methods.contains("GET"));
			assertTrue(methods.contains("POST"));
		}
		finally
		{
			request.releaseConnection();
		}
	}

	@Test
	public void shouldFailWithNotFound() throws Exception
	{
		HttpDelete request = new HttpDelete(SERVER_HOST + "/x/y/z.json");

		try
		{
			HttpResponse response = (HttpResponse) CLIENT.execute(request);
			assertEquals(HttpResponseStatus.NOT_FOUND.code(), response.getStatusLine().getStatusCode());
			HttpEntity entity = response.getEntity();
			assertTrue(entity.getContentLength() > 0l);
			assertEquals(ContentType.JSON, entity.getContentType().getValue());
			String json = EntityUtils.toString(entity);
			assertTrue(json.startsWith("{\"errorId\":"));
			assertTrue(json.contains("\"httpStatus\":404"));
			assertTrue(json.contains("\"message\":\"Unresolvable URL: " + SERVER_HOST + "/x/y/z.json\""));
			assertTrue(json.contains("\"errorType\":\"NotFoundException\""));
		}
		finally
		{
			request.releaseConnection();
		}
	}

	@Test
	public void shouldReturnJsonUsingFormat() throws Exception
	{
		HttpGet request = new HttpGet(URL1_JSON);

		try
		{
			HttpResponse response = (HttpResponse) CLIENT.execute(request);
			assertEquals(HttpResponseStatus.OK.code(), response.getStatusLine().getStatusCode());
			HttpEntity entity = response.getEntity();
			assertTrue(entity.getContentLength() > 0l);
			assertEquals(ContentType.JSON, entity.getContentType().getValue());
			assertEquals("\"read\"", EntityUtils.toString(entity));
		}
		finally
		{
			request.releaseConnection();
		}
	}

	@Test
	public void shouldReturnErrorOnCapitalizedFormat() throws Exception
	{
		HttpGet request = new HttpGet(URL1_PLAIN + ".JSON");

		try
		{
			HttpResponse response = (HttpResponse) CLIENT.execute(request);
			assertEquals(HttpResponseStatus.BAD_REQUEST.code(), response.getStatusLine().getStatusCode());
			HttpEntity entity = response.getEntity();
			assertTrue(entity.getContentLength() > 0l);
			assertEquals(ContentType.JSON, entity.getContentType().getValue());
			String json = EntityUtils.toString(entity);
			assertNotNull(json);
			assertTrue(json.startsWith("{\"errorId\":"));
			assertTrue(json.contains("\"httpStatus\":400"));
			assertTrue(json.contains("\"message\":\"Requested representation format not supported: JSON. Supported formats: "));
			assertTrue(json.contains("json"));
			assertTrue(json.contains("\"errorType\":\"BadRequestException\""));
		}
		finally
		{
			request.releaseConnection();
		}
	}

	@Test
	public void shouldReturnNonSerializedTextPlainResult() throws Exception
	{
		HttpGet request = new HttpGet(SERVER_HOST + "/unserialized");

		try
		{
			HttpResponse response = (HttpResponse) CLIENT.execute(request);
			assertEquals(HttpResponseStatus.OK.code(), response.getStatusLine().getStatusCode());
			HttpEntity entity = response.getEntity();
			assertTrue(entity.getContentLength() > 0l);
			assertEquals(ContentType.TEXT_PLAIN, entity.getContentType().getValue());
			assertEquals("read", EntityUtils.toString(entity));
		}
		finally
		{
			request.releaseConnection();
		}
	}

	@Test
	public void shouldFailWithBadRequest() throws Exception
	{
		HttpGet request = new HttpGet(URL1_PLAIN + ".xyz");

		try
		{
			HttpResponse response = (HttpResponse) CLIENT.execute(request);
			assertEquals(HttpResponseStatus.BAD_REQUEST.code(), response.getStatusLine().getStatusCode());
			HttpEntity entity = response.getEntity();
			assertTrue(entity.getContentLength() > 0l);
			assertEquals(ContentType.JSON, entity.getContentType().getValue());
			String json = EntityUtils.toString(entity);
			assertTrue(json.startsWith("{\"errorId\":"));
			assertTrue(json.contains("\"httpStatus\":400"));
			assertTrue(json.contains("\"message\":\"Requested representation format not supported: xyz. Supported formats: "));
			assertTrue(json.contains("json"));
			assertTrue(json.contains("\"errorType\":\"BadRequestException\""));
		}
		finally
		{
			request.releaseConnection();
		}
	}

	@Test
	public void shouldFailOnInvalidAccept() throws Exception
	{
		HttpGet request = new HttpGet(URL1_PLAIN);

		try
		{
			request.addHeader(HttpHeaderNames.ACCEPT.toString(), "application/nogood");
			HttpResponse response = (HttpResponse) CLIENT.execute(request);
			assertEquals(HttpResponseStatus.NOT_ACCEPTABLE.code(), response.getStatusLine().getStatusCode());
			HttpEntity entity = response.getEntity();
			assertTrue(entity.getContentLength() > 0l);
			assertEquals(ContentType.JSON, entity.getContentType().getValue());
			String json = EntityUtils.toString(entity);
			assertNotNull(json);
			assertTrue(json.startsWith("{\"errorId\":"));
			assertTrue(json.contains("\"httpStatus\":406"));
			assertTrue(json.contains("\"message\":\"Supported Media Types: application/json; charset=UTF-8, application/javascript; charset=UTF-8, text/javascript; charset=UTF-8, application/hal+json; charset=UTF-8\""));
			assertTrue(json.contains("\"errorType\":\"NotAcceptableException\""));
		}
		finally
		{
			request.releaseConnection();
		}
	}

	@Test
	public void shouldSerializeObjectAsJson() throws Exception
	{
		HttpGet request = new HttpGet(LITTLE_O_URL + ".json");

		try
		{
			HttpResponse response = (HttpResponse) CLIENT.execute(request);
			assertEquals(HttpResponseStatus.OK.code(), response.getStatusLine().getStatusCode());
			HttpEntity entity = response.getEntity();
			assertTrue(entity.getContentLength() > 0l);
			assertEquals(ContentType.JSON, entity.getContentType().getValue());
			LittleO o = DEFAULT_SERIALIZER.getSerializer(Format.JSON).deserialize(
			    EntityUtils.toString(entity), LittleO.class);
			verifyObject(o);
		}
		finally
		{
			request.releaseConnection();
		}
	}

	@Test
	public void shouldSerializeListAsJson() throws Exception
	{
		HttpGet request = new HttpGet(LITTLE_OS_URL + ".json");

		try
		{
			HttpResponse response = (HttpResponse) CLIENT.execute(request);
			assertEquals(HttpResponseStatus.OK.code(), response.getStatusLine().getStatusCode());
			HttpEntity entity = response.getEntity();
			assertTrue(entity.getContentLength() > 0l);
			assertEquals(ContentType.JSON, entity.getContentType().getValue());
			Header range = response.getFirstHeader(HttpHeaderNames.CONTENT_RANGE.toString());
			assertNotNull(range);
			assertEquals("items 0-2/3", range.getValue());
			LittleO[] result = DEFAULT_SERIALIZER.getSerializer(Format.JSON).deserialize(
			    EntityUtils.toString(entity), LittleO[].class);
			verifyList(result);
		}
		finally
		{
			request.releaseConnection();
		}
	}

	@Test
	public void shouldNotContainContentRangeHeaderOnInvalidAcceptHeader()
	throws Exception
	{
		HttpGet request = new HttpGet(LITTLE_OS_URL);

		try
		{
			request.addHeader(HttpHeaderNames.ACCEPT.toString(), "no-good/no-good");
			HttpResponse response = (HttpResponse) CLIENT.execute(request);
			assertEquals(HttpResponseStatus.NOT_ACCEPTABLE.code(), response
			    .getStatusLine().getStatusCode());
			HttpEntity entity = response.getEntity();
			assertTrue(entity.getContentLength() > 0l);
			assertEquals(ContentType.JSON, entity.getContentType().getValue());
			assertNull(response.getFirstHeader(HttpHeaderNames.CONTENT_RANGE.toString()));
			String json = EntityUtils.toString(entity);
			assertNotNull(json);
			assertTrue(json.startsWith("{\"errorId\":"));
			assertTrue(json.contains("\"httpStatus\":406"));
			assertTrue(json.contains("\"message\":\"Supported Media Types: application/json; charset=UTF-8, application/javascript; charset=UTF-8, text/javascript; charset=UTF-8, application/hal+json; charset=UTF-8\""));
			assertTrue(json.contains("\"errorType\":\"NotAcceptableException\""));
		}
		finally
		{
			request.releaseConnection();
		}
	}

	@Test
	public void shouldSerializeObjectAsXml() throws Exception
	{
		HttpGet request = new HttpGet(LITTLE_O_URL + ".xml");

		try
		{
			HttpResponse response = (HttpResponse) CLIENT.execute(request);
			assertEquals(HttpResponseStatus.BAD_REQUEST.code(), response.getStatusLine().getStatusCode());
		}
		finally
		{
			request.releaseConnection();
		}
	}

	@Test
	public void shouldSerializeApplicationHalJson() throws Exception
	{
		HttpGet request = new HttpGet(LITTLE_OS_URL);

		try
		{
			request.addHeader(HttpHeaderNames.ACCEPT.toString(), "application/hal+json");
			HttpResponse response = (HttpResponse) CLIENT.execute(request);
			assertEquals(HttpResponseStatus.OK.code(), response.getStatusLine().getStatusCode());
			HttpEntity entity = response.getEntity();
			assertTrue(entity.getContentLength() > 0l);
			assertEquals(ContentType.HAL_JSON, entity.getContentType().getValue());
			Header range = response.getFirstHeader(HttpHeaderNames.CONTENT_RANGE.toString());
			assertNotNull(range);
			assertEquals("items 0-2/3", range.getValue());
			String entityString = EntityUtils.toString(entity);
			LittleO[] os = DEFAULT_SERIALIZER.getSerializer(Format.JSON).deserialize(entityString, LittleO[].class);
			verifyList(os);
		}
		finally
		{
			request.releaseConnection();
		}
	}

	@Test
	public void shouldSerializeApplicationHalXml() throws Exception
	{
		HttpGet request = new HttpGet(LITTLE_OS_URL);

		try
		{
			request.addHeader(HttpHeaderNames.ACCEPT.toString(), "application/hal+xml");
			HttpResponse response = (HttpResponse) CLIENT.execute(request);
			assertEquals(HttpResponseStatus.NOT_ACCEPTABLE.code(), response.getStatusLine().getStatusCode());
		}
		finally
		{
			request.releaseConnection();
		}
	}

	@Test
	public void shouldSerializeUnmappedPreprocessorException() throws Exception
	{
		ERROR_PREPROCESSOR.shouldThrow(true);
		HttpGet request = new HttpGet(LITTLE_OS_URL);

		try
		{
			HttpResponse response = (HttpResponse) CLIENT.execute(request);
			assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR.code(), response.getStatusLine().getStatusCode());
			HttpEntity entity = response.getEntity();
			assertTrue(entity.getContentLength() > 0l);
			assertEquals(ContentType.JSON, entity.getContentType().getValue());
			assertNull(response.getFirstHeader(HttpHeaderNames.CONTENT_RANGE.toString()));
			String entityString = EntityUtils.toString(entity);
			assertNotNull(entityString);
			assertTrue(entityString.contains("\"errorId\":\""));
			assertTrue(entityString.contains("\"httpStatus\":500"));
			assertTrue(entityString.contains("\"message\":\"ErrorPreprocessor\""));
			assertTrue(entityString.contains("\"errorType\":\"RuntimeException\""));
		}
		finally
		{
			request.releaseConnection();
		}
	}

//	@Test
//	public void shouldSerializeMappedPreprocessorException() throws Exception
//	{
//		AbstractSerializationProvider serializer = new NullSerializationProvider();
//		JacksonJsonProcessor jsonProc = new JacksonJsonProcessor();
//		serializer.add(jsonProc, new ErrorResponseWrapper(), true);
//		RestExpress.setSerializationProvider(serializer);
//		RestExpress server = createServer();
//		server.addPreprocessor(new ErrorPreprocessor());
//		server.mapException(RuntimeException.class, BadRequestException.class);
//		server.bind(DEFAULT_PORT + 2);
//
//		HttpGet request = new HttpGet(onPort(DEFAULT_PORT + 2, LITTLE_OS_URL));
//
//		try
//		{
//			HttpResponse response = (HttpResponse) CLIENT.execute(request);
//			assertEquals(HttpResponseStatus.BAD_REQUEST.code(), response.getStatusLine().getStatusCode());
//			HttpEntity entity = response.getEntity();
//			assertTrue(entity.getContentLength() > 0l);
//			assertEquals(ContentType.JSON, entity.getContentType().getValue());
//			assertNull(response.getFirstHeader(HttpHeaderNames.CONTENT_RANGE.toString()));
//			String entityString = EntityUtils.toString(entity);
//			assertNotNull(entityString);
//			assertTrue(entityString.contains("\"errorId\":\""));
//			assertTrue(entityString.contains("\"httpStatus\":400"));
//			assertTrue(entityString.contains("\"message\":\"ErrorPreprocessor\""));
//			assertTrue(entityString.contains("\"errorType\":\"RuntimeException\""));
//		}
//		finally
//		{
//			request.releaseConnection();
//			server.shutdown(true);
//		}
//	}

	@Test
	public void shouldSerializeUnmappedException() throws Exception
	{
		HttpGet request = new HttpGet(URL_EXCEPTION_LITTLE_O);

		try
		{
			HttpResponse response = (HttpResponse) CLIENT.execute(request);
			assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR.code(), response.getStatusLine().getStatusCode());
			HttpEntity entity = response.getEntity();
			assertTrue(entity.getContentLength() > 0l);
			assertEquals(ContentType.JSON, entity.getContentType().getValue());
			assertNull(response.getFirstHeader(HttpHeaderNames.CONTENT_RANGE.toString()));
			String entityString = EntityUtils.toString(entity);
			assertNotNull(entityString);
			assertTrue(entityString.contains("\"errorId\":\""));
			assertTrue(entityString.contains("\"httpStatus\":500"));
			assertTrue(entityString.contains("\"message\":\"ObjectTestController\""));
			assertTrue(entityString.contains("\"errorType\":\"NullPointerException\""));
		}
		finally
		{
			request.releaseConnection();
		}
	}

//	@Test
//	public void shouldSerializeMappedException() throws Exception
//	{
//		SERVER.mapException(NullPointerException.class, BadRequestException.class);
//
//		HttpGet request = new HttpGet(URL_EXCEPTION_LITTLE_O);
//
//		try
//		{
//			HttpResponse response = (HttpResponse) CLIENT.execute(request);
//			assertEquals(HttpResponseStatus.BAD_REQUEST.code(), response.getStatusLine().getStatusCode());
//			HttpEntity entity = response.getEntity();
//			assertTrue(entity.getContentLength() > 0l);
//			assertEquals(ContentType.JSON, entity.getContentType().getValue());
//			assertNull(response.getFirstHeader(HttpHeaderNames.CONTENT_RANGE.toString()));
//			String entityString = EntityUtils.toString(entity);
//			assertNotNull(entityString);
//			assertTrue(entityString.contains("\"errorId\":\""));
//			assertTrue(entityString.contains("\"httpStatus\":400"));
//			assertTrue(entityString.contains("\"message\":\"ObjectTestController\""));
//			assertTrue(entityString.contains("\"errorType\":\"NullPointerException\""));
//		}
//		finally
//		{
//			request.releaseConnection();
//		}
//	}

	@Test
	public void shouldCallFinallyProcessorOnException() throws Exception
	{
		HttpGet request = new HttpGet(URL_EXCEPTION_LITTLE_O);

		try
		{
			HttpResponse response = (HttpResponse) CLIENT.execute(request);
			assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR.code(), response.getStatusLine().getStatusCode());
			assertEquals(1, POSTPROCESSOR.callCount());
		}
		finally
		{
			request.releaseConnection();
		}
	}

	@Test
	public void shouldCallFinallyProcessorOnPreprocessorException()
	throws Exception
	{
		ERROR_PREPROCESSOR.shouldThrow(true);
		HttpGet request = new HttpGet(LITTLE_OS_URL);

		try
		{
			HttpResponse response = (HttpResponse) CLIENT.execute(request);
			assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR.code(), response.getStatusLine().getStatusCode());
			assertEquals(1, POSTPROCESSOR.callCount());
			String entityString = EntityUtils.toString(response.getEntity());
			assertNotNull(entityString);
			assertTrue(entityString.contains("\"errorId\":\""));
			assertTrue(entityString.contains("\"httpStatus\":500"));
			assertTrue(entityString.contains("\"message\":\"ErrorPreprocessor\""));
			assertTrue(entityString.contains("\"errorType\":\"RuntimeException\""));
		}
		finally
		{
			request.releaseConnection();
		}
	}

	@Test
	public void shouldBeAbleToEncodeGZip() throws Exception
	{
		HttpGet request = new HttpGet(URL1_PLAIN);

		try
		{
			request.addHeader(HttpHeaderNames.ACCEPT_ENCODING.toString(), "gzip");
			HttpResponse response = (HttpResponse) CLIENT.execute(request);
			assertEquals(HttpResponseStatus.OK.code(), response.getStatusLine().getStatusCode());
	
			BufferedReader contentBuffer = new BufferedReader(
			    new InputStreamReader(new GZIPInputStream(response.getEntity().getContent())));
	
			String lineIn;
			String decodedMessageString = "";
			while ((lineIn = contentBuffer.readLine()) != null)
			{
				decodedMessageString += lineIn;
			}
	
			HttpEntity entity = response.getEntity();
			assertEquals("\"read\"", decodedMessageString);
			assertEquals(ContentType.JSON, entity.getContentType().getValue());
			assertEquals("gzip", entity.getContentEncoding().getValue());
		}
		finally
		{
			request.releaseConnection();
		}
	}

	@Test
	public void shouldBeAbleToEncodeDeflate() throws Exception
	{
		HttpGet request = new HttpGet(URL1_PLAIN);

		try
		{
			request.addHeader(HttpHeaderNames.ACCEPT_ENCODING.toString(), "deflate");
			HttpResponse response = (HttpResponse) CLIENT.execute(request);
			assertEquals(HttpResponseStatus.OK.code(), response.getStatusLine().getStatusCode());
	
			BufferedReader contentBuffer = new BufferedReader(
			    new InputStreamReader(new InflaterInputStream(response.getEntity().getContent())));
	
			String lineIn;
			String decodedMessageString = "";
	
			while ((lineIn = contentBuffer.readLine()) != null)
			{
				decodedMessageString += lineIn;
			}
	
			HttpEntity entity = response.getEntity();
			assertEquals("\"read\"", decodedMessageString);
			assertEquals(ContentType.JSON, entity.getContentType().getValue());
			assertEquals("deflate", entity.getContentEncoding().getValue());
		}
		finally
		{
			request.releaseConnection();
		}
	}

	@Test
	public void shouldBeAbleToDecodeGZip() throws Exception
	{
		HttpPut request = new HttpPut(URL_ECHO);

		try
		{
			request.addHeader(HttpHeaderNames.CONTENT_ENCODING.toString(), "gzip");
	
			BasicHttpEntity requestEntity = new BasicHttpEntity();
	
			ByteArrayOutputStream byteArrayOut = new ByteArrayOutputStream();
			GZIPOutputStream gzipOutput = new GZIPOutputStream(byteArrayOut);
			gzipOutput.write("STRING".getBytes("UTF-8"));
			gzipOutput.close();
	
			requestEntity.setContent(new ByteArrayInputStream(byteArrayOut.toByteArray()));
	
			request.setEntity(requestEntity);
			HttpResponse response = (HttpResponse) CLIENT.execute(request);
			assertEquals(HttpResponseStatus.OK.code(), response.getStatusLine().getStatusCode());
			HttpEntity responseEntity = response.getEntity();
			assertTrue(responseEntity.getContentLength() > 0l);
			assertEquals(ContentType.JSON, responseEntity.getContentType().getValue());
			assertEquals("\"STRING\"", EntityUtils.toString(responseEntity));
		}
		finally
		{
			request.releaseConnection();
		}
	}

	@Test
	public void shouldBeAbleToDecodeDeflate() throws Exception
	{
		HttpPut request = new HttpPut(URL_ECHO);

		try
		{
			request.addHeader(HttpHeaderNames.CONTENT_ENCODING.toString(), "deflate");
			BasicHttpEntity requestEntity = new BasicHttpEntity();
			ByteArrayOutputStream byteArrayOut = new ByteArrayOutputStream();
			DeflaterOutputStream inflaterOutput = new DeflaterOutputStream(byteArrayOut);
			inflaterOutput.write("STRING".getBytes("UTF-8"));
			inflaterOutput.close();
	
			requestEntity.setContent(new ByteArrayInputStream(byteArrayOut.toByteArray()));
	
			request.setEntity(requestEntity);
			HttpResponse response = (HttpResponse) CLIENT.execute(request);
			assertEquals(HttpResponseStatus.OK.code(), response.getStatusLine().getStatusCode());
			HttpEntity responseEntity = response.getEntity();
			assertTrue(responseEntity.getContentLength() > 0l);
			assertEquals(ContentType.JSON, responseEntity.getContentType().getValue());
			assertEquals("\"STRING\"", EntityUtils.toString(responseEntity));
		}
		finally
		{
			request.releaseConnection();
		}
	}

	private void verifyObject(LittleO o)
	{
		assertNotNull(o);
		LittleO expected = new LittleO();
		assertEquals(expected.getName(), o.getName());
		assertEquals(expected.getInteger(), o.getInteger());
		assertEquals(expected.isBoolean(), o.isBoolean());
		assertEquals(3, o.getChildren().size());
		assertEquals(3, o.getArray().length);
	}

	private void verifyList(LittleO[] result)
	{
		assertEquals(3, result.length);
		assertEquals("name", result[0].getName());
	}

	// SECTION: INNER CLASSES

	@SuppressWarnings("unused")
	private class StringTestController
	{
		public String create(Request request, Response response)
		{
			response.setResponseCreated();
			return "create";
		}

		public String read(Request request, Response response)
		{
			return "read";
		}

		public String update(Request request, Response response)
		{
			return "update";
		}

		public String delete(Request request, Response response)
		{
			return "delete";
		}

		public String readAll(Request request, Response response)
		{
			return "readAll";
		}

		public void throwException(Request request, Response response)
		throws Exception
		{
			throw new NullPointerException(this.getClass().getSimpleName());
		}
	}

	@SuppressWarnings("unused")
	private class ObjectTestController
	{
		public LittleO read(Request request, Response Response)
		{
			return newLittleO(3);
		}

		public void throwException(Request request, Response response)
		throws Exception
		{
			throw new NullPointerException(this.getClass().getSimpleName());
		}

		public List<LittleO> readAll(Request request, Response response)
		{
			QueryRange range = new QueryRange(0, 3);
			response.addRangeHeader(range, 3);
			List<LittleO> l = new ArrayList<LittleO>();
			l.add(newLittleO(1));
			l.add(newLittleO(2));
			l.add(newLittleO(3));
			return l;
		}

		private LittleO newLittleO(int count)
		{
			LittleO l = new LittleO();
			List<LittleO> list = new ArrayList<LittleO>(count);

			for (int i = 0; i < count; i++)
			{
				list.add(new LittleO());
			}

			l.setChildren(list);
			return l;
		}
	}

	@SuppressWarnings("unused")
	private class EchoTestController
	{
		public String create(Request request, Response response)
		{
			response.setResponseCreated();
			byte[] b = new byte[request.getBody().capacity()];
			request.getBody().readBytes(b);
			return new String(b);
		}

		public String read(Request request, Response response)
		{
			byte[] b = new byte[request.getBody().capacity()];
			request.getBody().readBytes(b);
			return new String(b);
		}

		public String update(Request request, Response response)
		{
			byte[] b = new byte[request.getBody().capacity()];
			request.getBody().readBytes(b);
			return new String(b);
		}

		public String delete(Request request, Response response)
		{
			byte[] b = new byte[request.getBody().capacity()];
			request.getBody().readBytes(b);
			return new String(b);
		}

		public String readAll(Request request, Response response)
		{
			byte[] b = new byte[request.getBody().capacity()];
			request.getBody().readBytes(b);
			return new String(b);
		}

		public void throwException(Request request, Response response)
		throws Exception
		{
			throw new NullPointerException(this.getClass().getSimpleName());
		}
	}
}
