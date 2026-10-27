/*
    Copyright 2011, Strategic Gains, Inc.

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
package org.restexpress;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.BindException;
import java.util.UUID;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.AfterClass;
import org.junit.Test;
import org.restexpress.exception.NoRoutesDefinedException;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;


/**
 * @author toddf
 * @since Jan 28, 2011
 */
public class RestExpressTest
{
	private static final CloseableHttpClient CLIENT = HttpClientBuilder.create().build();
	private static final String TEST_PATH = "/restexpress/test1";
	private static final int TEST_PORT = 8901;
	private static final String TEST_URL_PATTERN = "http://localhost:%s" + TEST_PATH;

	private static int nextPort = TEST_PORT;

	@AfterClass
	public static void afterTest()
	throws IOException
	{
		CLIENT.close();
	}

	@Test
	public void shouldNotUseSystemOut()
	{
		RestExpress re = new RestExpress();
		re.noSystemOut();
		assertFalse(re.shouldUseSystemOut());
	}

	@Test(expected=BindException.class)
	public void shouldThrowOnPortInUse()
	throws Throwable
	{
		int port = nextPort();
		RestExpress re = new RestExpress();
		NoopController controller = new NoopController();
		re.uri(TEST_PATH, controller);
		re.bind(port);

		waitForStartup();

		RestExpress re2 = new RestExpress();
		re2.uri(TEST_PATH, controller);
		re2.bind(port);
	}

	@Test
	public void shouldCallDefaultMethods()
	throws Throwable
	{
		int port = nextPort();
		String testUrl = createUrl(TEST_URL_PATTERN, port);
		RestExpress re = new RestExpress();
		NoopController controller = new NoopController();
		re.uri(TEST_PATH, controller);
		re.bind(port);

		waitForStartup();

		HttpGet get = new HttpGet(testUrl);
		try
		{
			HttpResponse response = (HttpResponse) CLIENT.execute(get);
			assertEquals(200, response.getStatusLine().getStatusCode());
			assertEquals(1, controller.read);
			assertEquals(0, controller.create);
			assertEquals(0, controller.update);
			assertEquals(0, controller.delete);
			assertEquals(0, controller.options);
			assertEquals(0, controller.head);
			assertEquals(0, controller.patch);
		}
		finally
		{
			get.releaseConnection();
		}
		
		HttpPost post = new HttpPost(testUrl);
		try
		{
			HttpResponse response = (HttpResponse) CLIENT.execute(post);
			assertEquals(201, response.getStatusLine().getStatusCode());
			assertEquals(1, controller.create);
			assertEquals(1, controller.read);
			assertEquals(0, controller.update);
			assertEquals(0, controller.delete);
			assertEquals(0, controller.options);
			assertEquals(0, controller.head);
			assertEquals(0, controller.patch);
		}
		finally
		{
			post.releaseConnection();
		}

		HttpPut put = new HttpPut(testUrl);
		try
		{
			HttpResponse response = (HttpResponse) CLIENT.execute(put);
			assertEquals(200, response.getStatusLine().getStatusCode());
			assertEquals(1, controller.update);
			assertEquals(1, controller.read);
			assertEquals(1, controller.create);
			assertEquals(0, controller.delete);
			assertEquals(0, controller.options);
			assertEquals(0, controller.head);
			assertEquals(0, controller.patch);
		}
		finally
		{
			put.releaseConnection();
		}

		HttpDelete delete = new HttpDelete(testUrl);
		try
		{
			HttpResponse response = (HttpResponse) CLIENT.execute(delete);
			assertEquals(200, response.getStatusLine().getStatusCode());
			assertEquals(1, controller.delete);
			assertEquals(1, controller.read);
			assertEquals(1, controller.create);
			assertEquals(1, controller.update);
			assertEquals(0, controller.options);
			assertEquals(0, controller.head);
			assertEquals(0, controller.patch);
		}
		finally
		{
			delete.releaseConnection();
		}

		HttpOptions options = new HttpOptions(testUrl);
		try
		{
			HttpResponse response = (HttpResponse) CLIENT.execute(options);
			assertEquals(405, response.getStatusLine().getStatusCode());
		}
		finally
		{
			options.releaseConnection();
		}

		re.shutdown(true);
	}

	@Test
	public void shouldCallAltMethods()
	throws Throwable
	{
		int port = nextPort();
		String testUrl = createUrl(TEST_URL_PATTERN, port);
		RestExpress re = new RestExpress();
		NoopController controller = new NoopController();
		re.uri(TEST_PATH, controller)
			.method(HttpMethod.HEAD, HttpMethod.OPTIONS, HttpMethod.PATCH);
		re.bind(port);

		waitForStartup();

		HttpGet get = new HttpGet(testUrl);
		try
		{
			HttpResponse response = (HttpResponse) CLIENT.execute(get);
			assertEquals(405, response.getStatusLine().getStatusCode());			
		}
		finally
		{
			get.releaseConnection();
		}

		HttpOptions options = new HttpOptions(testUrl);
		try
		{
			HttpResponse response = (HttpResponse) CLIENT.execute(options);
			assertEquals(200, response.getStatusLine().getStatusCode());
			assertEquals(0, controller.delete);
			assertEquals(0, controller.read);
			assertEquals(0, controller.create);
			assertEquals(0, controller.update);
			assertEquals(1, controller.options);
			assertEquals(0, controller.head);
			assertEquals(0, controller.patch);
		}
		finally
		{
			options.releaseConnection();
		}

		HttpHead head = new HttpHead(testUrl);
		try
		{
			HttpResponse response = (HttpResponse) CLIENT.execute(head);
			assertEquals(200, response.getStatusLine().getStatusCode());
			assertEquals(0, controller.delete);
			assertEquals(0, controller.read);
			assertEquals(0, controller.create);
			assertEquals(0, controller.update);
			assertEquals(1, controller.options);
			assertEquals(1, controller.head);
			assertEquals(0, controller.patch);
		}
		finally
		{
			head.releaseConnection();
		}

		HttpPatch patch = new HttpPatch(testUrl);
		try
		{
			HttpResponse response = (HttpResponse) CLIENT.execute(patch);
			assertEquals(200, response.getStatusLine().getStatusCode());
			assertEquals(0, controller.delete);
			assertEquals(0, controller.read);
			assertEquals(0, controller.create);
			assertEquals(0, controller.update);
			assertEquals(1, controller.options);
			assertEquals(1, controller.head);
			assertEquals(1, controller.patch);
		}
		finally
		{
			patch.releaseConnection();
		}

		re.shutdown(true);
	}

	@Test
	public void shouldCallAltNamedMethods()
	throws Throwable
	{
		int port = nextPort();
		String testUrl = createUrl(TEST_URL_PATTERN, port);
		RestExpress re = new RestExpress();
		AltController controller = new AltController();
		re.uri(TEST_PATH, controller)
			.action("altHead", HttpMethod.HEAD)
			.action("altOptions", HttpMethod.OPTIONS)
			.action("altPatch", HttpMethod.PATCH);
		re.bind(port);

		waitForStartup();

		HttpGet get = new HttpGet(testUrl);
		try
		{
			HttpResponse response = (HttpResponse) CLIENT.execute(get);
			assertEquals(405, response.getStatusLine().getStatusCode());
		}
		finally
		{
			get.releaseConnection();
		}

		HttpOptions options = new HttpOptions(testUrl);
		try
		{
			HttpResponse response = (HttpResponse) CLIENT.execute(options);
			assertEquals(200, response.getStatusLine().getStatusCode());
			assertEquals(1, controller.options);
			assertEquals(0, controller.head);
			assertEquals(0, controller.patch);
		}
		finally
		{
			options.releaseConnection();
		}

		HttpHead head = new HttpHead(testUrl);
		try
		{
			HttpResponse response = (HttpResponse) CLIENT.execute(head);
			assertEquals(200, response.getStatusLine().getStatusCode());
			assertEquals(1, controller.options);
			assertEquals(1, controller.head);
			assertEquals(0, controller.patch);
		}
		finally
		{
			head.releaseConnection();
		}

		HttpPatch patch = new HttpPatch(testUrl);
		try
		{
			HttpResponse response = (HttpResponse) CLIENT.execute(patch);
			assertEquals(200, response.getStatusLine().getStatusCode());
			assertEquals(1, controller.options);
			assertEquals(1, controller.head);
			assertEquals(1, controller.patch);
		}
		finally
		{
			patch.releaseConnection();
		}

		re.shutdown(true);
	}

	@Test
	public void shouldSetOutputMediaType()
	throws Throwable
	{
		int port = nextPort();
		String testUrl = createUrl(TEST_URL_PATTERN, port);
		RestExpress re = new RestExpress();
		NoopController controller = new NoopController();
		re.uri(TEST_PATH, controller);
		re.bind(port);

		waitForStartup();

		HttpPost post = new HttpPost(testUrl);
		try
		{
			post.addHeader(HttpHeaderNames.ACCEPT.toString(), "application/json");
			HttpResponse response = (HttpResponse) CLIENT.execute(post);
			assertEquals(201, response.getStatusLine().getStatusCode());
			assertEquals(ContentType.JSON, controller.outputMediaType);
		}
		finally
		{
			post.releaseConnection();
		}

		HttpGet get = new HttpGet(testUrl);
		try
		{
			get.addHeader(HttpHeaderNames.ACCEPT.toString(), "application/json");
			HttpResponse response = (HttpResponse) CLIENT.execute(get);
			assertEquals(200, response.getStatusLine().getStatusCode());
			assertEquals(ContentType.JSON, controller.outputMediaType);
		}
		finally
		{
			get.releaseConnection();
		}

		HttpPut put = new HttpPut(testUrl);
		try
		{
			put.addHeader(HttpHeaderNames.ACCEPT.toString(), "application/json");
			HttpResponse response = (HttpResponse) CLIENT.execute(put);
			assertEquals(200, response.getStatusLine().getStatusCode());
			assertEquals(ContentType.JSON, controller.outputMediaType);
		}
		finally
		{
			put.releaseConnection();
		}

		HttpDelete delete = new HttpDelete(testUrl);
		try
		{
			delete.addHeader(HttpHeaderNames.ACCEPT.toString(), "application/json");
			HttpResponse response = (HttpResponse) CLIENT.execute(delete);
			assertEquals(200, response.getStatusLine().getStatusCode());
			assertEquals(ContentType.JSON, controller.outputMediaType);
		}
		finally
		{
			delete.releaseConnection();
		}

		re.shutdown();
	}

	@Test(expected=NoRoutesDefinedException.class)
	public void shouldThrowNoRoutesDefinedException()
	throws Throwable
	{
		RestExpress re = null;

		try
		{
			re = new RestExpress();
			re.bind(nextPort());
		}
		finally
		{
			re.shutdown();
		}
	}

	@Test
	public void shouldUploadFile() throws Throwable {
		RestExpress re = new RestExpress();
		re.setSupportFileUpload(true);
		NoopController controller = new NoopController();
		re.uri(TEST_PATH, controller)
			.action("upload", HttpMethod.POST);
		re.bind(TEST_PORT);

		CloseableHttpClient client = HttpClientBuilder.create().build();
		int port = nextPort();
		String testUrl = createUrl(TEST_URL_PATTERN, port);
		HttpPost post = new HttpPost(testUrl);
		post.addHeader("log_id", UUID.randomUUID().toString());
		RandomAccessFile rf = new RandomAccessFile("file1", "rw");
		rf.setLength(50 * 1024);
		rf.close();
		File f1 = new File("file1");
		MultipartEntityBuilder ab = MultipartEntityBuilder.create();
		FileBody fileBody = new FileBody(f1, org.apache.http.entity.ContentType.DEFAULT_BINARY);
		ab.addPart("upload", fileBody);
		HttpEntity entity = ab.build();
		post.setEntity(entity);

		try
		{
			HttpResponse response = (HttpResponse) client.execute(post);
			assertEquals(201, response.getStatusLine().getStatusCode());
			File f = controller.uploaded;
			assertTrue(f.exists());
			assertTrue(f.canRead());
			assertEquals((50*1024), f.length());
		}
		finally
		{
			post.releaseConnection();
			client.close();
		}
	}

	public class NoopController
    {
		int create, read, update, delete, options, head, patch = 0;
		String outputMediaType;
		File uploaded;

		public void create(Request req, Response res)
		{
			++create;
			res.setResponseCreated();
			outputMediaType = res.getSerializationSettings().getMediaType();
		}

		public void read(Request req, Response res)
		{
			++read;
			outputMediaType = res.getSerializationSettings().getMediaType();
		}

		public void update(Request req, Response res)
		{
			++update;
			outputMediaType = res.getSerializationSettings().getMediaType();
		}

		public void delete(Request req, Response res)
		{
			++delete;
			outputMediaType = res.getSerializationSettings().getMediaType();
		}

		public void options(Request req, Response res)
		{
			++options;
		}

		public void head(Request req, Response res)
		{
			++head;
		}

		public void patch(Request req, Response res)
		{
			++patch;
		}

		public void upload(Request req, Response res)
		{
			uploaded = req.getUploadedFileAttachment();
			res.setResponseCreated();
		}
    }

	public class AltController
    {
		int options, head, patch = 0;
		String outputMediaType;

		public void altOptions(Request req, Response res)
		{
			++options;
		}

		public void altHead(Request req, Response res)
		{
			++head;
		}

		public void altPatch(Request req, Response res)
		{
			++patch;
		}
    }

	private synchronized int nextPort()
	{
		return nextPort++;
	}

	private String createUrl(String testUrl, int port)
	{
		return String.format(testUrl, port);
	}

	private void waitForStartup()
	{
		try
		{
			Thread.sleep(500L);
		}
		catch (InterruptedException e)
		{
			e.printStackTrace();
		}
	}
}
