package org.restexpress;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;

public class ServerNoCompressionTest
{
	private static final int DEFAULT_PORT = 8802;
	private static final String SERVER_HOST = "http://localhost:" + DEFAULT_PORT;
	private static final String URL_PATTERN1 = "/1/restexpress/{id}/test/{test}.{format}";
	private static final String URL_PATH1 = "/1/restexpress/sam/test/42";
	private static final String URL1_PLAIN = SERVER_HOST + URL_PATH1;
	private static final String ECHO_PATTERN = "/echo";
	private static final String URL_ECHO = SERVER_HOST + ECHO_PATTERN;

	private static final CloseableHttpClient CLIENT = new DefaultHttpClient();
//	private static final HttpClient CLIENT = HttpClientBuilder.create().useSystemProperties().build();

	private static RestExpress SERVER;

	public RestExpress createServer()
	{
		RestExpress server = new RestExpress();
		StringTestController stringTestController = new StringTestController();
		EchoTestController echoTestController = new EchoTestController();

		server.noCompression();
		server.uri(URL_PATTERN1, stringTestController);

		server.uri(ECHO_PATTERN, echoTestController)
			.action("update", HttpMethod.PUT);

		server.alias("littleObject", LittleO.class);
		return server;
	}

	@Before
	public void ensureServerRunning()
	throws Throwable
	{
		if (SERVER == null)
		{
			SERVER = createServer();
			SERVER.bind(DEFAULT_PORT);

			Thread.sleep(500L);
		}
	}

	@AfterClass
	public static void shutdownServer() throws IOException
	{
		CLIENT.close();
		SERVER.shutdown(true);
	}

	@Test
	public void shouldNotEncodeGZip() throws Exception
	{
		HttpGet request = new HttpGet(URL1_PLAIN);

		try
		{
			request.addHeader(HttpHeaderNames.ACCEPT_ENCODING.toString(), "gzip");
			HttpResponse response = (HttpResponse) CLIENT.execute(request);
			assertEquals(HttpResponseStatus.OK.code(), response.getStatusLine().getStatusCode());
	
			HttpEntity entity = response.getEntity();
			BufferedReader contentBuffer = new BufferedReader(new InputStreamReader(entity.getContent()));
	
			String lineIn;
			StringBuilder sb = new StringBuilder();

			while ((lineIn = contentBuffer.readLine()) != null)
			{
				sb.append(lineIn);
			}
	
			assertEquals("\"read\"", sb.toString());
			assertEquals(ContentType.JSON, entity.getContentType().getValue());
			assertNull(entity.getContentEncoding());
		}
		finally
		{
			request.releaseConnection();
		}
	}

	@Test
	public void shouldNotEncodeDeflate() throws Exception
	{
		HttpGet request = new HttpGet(URL1_PLAIN);

		try
		{
			request.addHeader(HttpHeaderNames.ACCEPT_ENCODING.toString(), "deflate");
			HttpResponse response = (HttpResponse) CLIENT.execute(request);
			assertEquals(HttpResponseStatus.OK.code(), response.getStatusLine().getStatusCode());
	
			HttpEntity entity = response.getEntity();
			BufferedReader contentBuffer = new BufferedReader(new InputStreamReader(entity.getContent()));

			String lineIn;
			StringBuilder sb = new StringBuilder();

			while ((lineIn = contentBuffer.readLine()) != null)
			{
				sb.append(lineIn);
			}

			assertEquals("\"read\"", sb.toString());
			assertEquals(ContentType.JSON, entity.getContentType().getValue());
			assertNull(entity.getContentEncoding());
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
