package org.restexpress.pipeline;

import static org.junit.Assert.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.RestExpress;
import org.restexpress.response.HttpResponseWriter;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.AttributeKey;

public class FileUploadHandlerTest
{
	private static final AttributeKey<MessageContext> CONTEXT = AttributeKey.valueOf("context");
	private final List<EmbeddedChannel> channels = new ArrayList<>();
	private final List<File> files = new ArrayList<>();
	private FileUploadHandler handler;
	private boolean failResponse;

	@Before
	public void initialize()
	{
		RestExpress server = new RestExpress();
		server.uri("/upload", new UploadController()).action("upload", HttpMethod.POST);
		handler = server.buildFileUploadRequestHandler();
		handler.setResponseWriter(new HttpResponseWriter()
		{
			public void write(ChannelHandlerContext ctx, Request request, Response response)
			{
				if (failResponse) throw new IllegalStateException("Response failed");
			}
		});
	}

	@After
	public void cleanup() throws Exception
	{
		for (EmbeddedChannel channel : channels) channel.finishAndReleaseAll();
		for (File file : files) Files.deleteIfExists(file.toPath());
	}

	@Test
	public void shouldCleanUpCompletedAndRepeatedUploads() throws Exception
	{
		EmbeddedChannel channel = channel();
		for (int i = 0; i < 2; ++i)
		{
			ByteBuf requestBody = start(channel, "payload-" + i);
			finish(channel);
			channel.checkException();
			assertEquals(0, requestBody.refCnt());
			assertNull(channel.attr(CONTEXT).get());
			assertEquals("payload-" + i, Files.readString(files.get(i).toPath()));
		}
	}

	@Test
	public void shouldKeepOverlappingConnectionsIndependent() throws Exception
	{
		EmbeddedChannel first = channel();
		EmbeddedChannel second = channel();
		ByteBuf firstBody = start(first, "first");
		ByteBuf secondBody = start(second, "second");
		finish(first);
		finish(second);
		first.checkException();
		second.checkException();
		assertEquals(0, firstBody.refCnt());
		assertEquals(0, secondBody.refCnt());
		assertEquals("first", Files.readString(files.get(0).toPath()));
		assertEquals("second", Files.readString(files.get(1).toPath()));
		assertNotEquals(files.get(0), files.get(1));
	}

	@Test
	public void shouldReleaseResourcesOnDisconnect()
	{
		EmbeddedChannel channel = channel();
		ByteBuf requestBody = start(channel, "unfinished");
		channel.close();
		channel.runPendingTasks();
		assertEquals(0, requestBody.refCnt());
		assertNull(channel.attr(CONTEXT).get());
	}

	@Test
	public void shouldReleaseResourcesWhenRemoved()
	{
		EmbeddedChannel channel = channel();
		ByteBuf requestBody = start(channel, "unfinished");
		channel.pipeline().remove(handler);
		assertEquals(0, requestBody.refCnt());
		assertNull(channel.attr(CONTEXT).get());
	}

	@Test
	public void shouldCleanUpAndReportResponseFailure()
	{
		EmbeddedChannel channel = channel();
		ByteBuf requestBody = start(channel, "failure");
		failResponse = true;
		try
		{
			finish(channel);
			fail("Expected response failure");
		}
		catch (IllegalStateException expected)
		{
			assertEquals("Response failed", expected.getMessage());
		}
		assertEquals(0, requestBody.refCnt());
		assertNull(channel.attr(CONTEXT).get());
		assertFalse(channel.isOpen());
	}

	@Test
	public void shouldCleanUpOnPipelineException()
	{
		EmbeddedChannel channel = channel();
		ByteBuf requestBody = start(channel, "unfinished");
		channel.pipeline().fireExceptionCaught(new IllegalStateException("Decoder failed"));
		try
		{
			channel.checkException();
			fail("Expected pipeline failure");
		}
		catch (IllegalStateException expected)
		{
			assertEquals("Decoder failed", expected.getMessage());
		}
		assertEquals(0, requestBody.refCnt());
		assertNull(channel.attr(CONTEXT).get());
		assertFalse(channel.isOpen());
	}

	private EmbeddedChannel channel()
	{
		EmbeddedChannel channel = new EmbeddedChannel(handler);
		channels.add(channel);
		return channel;
	}

	private ByteBuf start(EmbeddedChannel channel, String value)
	{
		DefaultHttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/upload");
		request.headers().set(HttpHeaderNames.CONTENT_TYPE, "multipart/form-data; boundary=testBoundary");
		channel.writeInbound(request);
		ByteBuf body = channel.attr(CONTEXT).get().getRequest().getBody();
		String part = "--testBoundary\r\nContent-Disposition: form-data; name=\"upload\"; filename=\"test.txt\""
			+ "\r\nContent-Type: application/octet-stream\r\n\r\n" + value;
		channel.writeInbound(new DefaultHttpContent(Unpooled.copiedBuffer(part, StandardCharsets.UTF_8)));
		return body;
	}

	private void finish(EmbeddedChannel channel)
	{
		channel.writeInbound(new DefaultLastHttpContent(
			Unpooled.copiedBuffer("\r\n--testBoundary--\r\n", StandardCharsets.UTF_8)));
	}

	public class UploadController
	{
		public void upload(Request request, Response response)
		{
			files.add(request.getUploadedFileAttachment());
		}
	}
}
