package org.restexpress.url;

import static org.junit.Assert.*;

import org.junit.Test;
import org.restexpress.Request;

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;

public class BaseUrlResolverTest
{
	@Test
	public void shouldResolveFromHost()
	{
		FullHttpRequest httpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/foo?param1=bar&param2=blah&yada");
		httpRequest.headers().add("Host", "testing-host");
		Request request = new Request(httpRequest, null);
		BaseUrl baseUrl = BaseUrlResolver.resolve(request);
		assertEquals("https", baseUrl.scheme);
		assertEquals("testing-host", baseUrl.host);
		assertNull(baseUrl.port);
		assertEquals("https://testing-host", baseUrl.toString());
	}

	@Test
	public void shouldResolveFromForwarded()
	{
		FullHttpRequest httpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/foo?param1=bar&param2=blah&yada");
		httpRequest.headers().add("Host", "testing-host");
		httpRequest.headers().add("Forwarded", "host=forwarded-host;proto=http");
		Request request = new Request(httpRequest, null);
		BaseUrl baseUrl = BaseUrlResolver.resolve(request);
		assertEquals("http", baseUrl.scheme);
		assertEquals("forwarded-host", baseUrl.host);
		assertNull(baseUrl.port);
		assertEquals("http://forwarded-host", baseUrl.toString());
	}

	@Test
	public void shouldResolveFromXForwarded()
	{
		FullHttpRequest httpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/foo?param1=bar&param2=blah&yada");
		httpRequest.headers().add("Host", "testing-host");
		httpRequest.headers().add("X-Forwarded-Host", "x-host");
		httpRequest.headers().add("X-Forwarded-Proto", "http");
		httpRequest.headers().add("X-Forwarded-Port", "8888");
		Request request = new Request(httpRequest, null);
		BaseUrl baseUrl = BaseUrlResolver.resolve(request);
		assertEquals("http", baseUrl.scheme);
		assertEquals("x-host", baseUrl.host);
		assertEquals("8888", baseUrl.port);
		assertEquals("http://x-host:8888", baseUrl.toString());
	}

	@Test
	public void shouldIncludedNonStandardXForwardedPort()
	{
		FullHttpRequest httpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/foo?param1=bar&param2=blah&yada");
		httpRequest.headers().add("Host", "testing-host");
		httpRequest.headers().add("X-Forwarded-Host", "example-host");
		httpRequest.headers().add("X-Forwarded-Proto", "https");
		httpRequest.headers().add("X-Forwarded-Port", "8443");
		Request request = new Request(httpRequest, null);
		BaseUrl baseUrl = BaseUrlResolver.resolve(request);
		assertEquals("https", baseUrl.scheme);
		assertEquals("example-host", baseUrl.host);
		assertEquals("8443", baseUrl.port);
		assertEquals("https://example-host:8443", baseUrl.toString());
	}

	@Test
	public void shouldIgnoreHttpsPort()
	{
		FullHttpRequest httpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/foo?param1=bar&param2=blah&yada");
		httpRequest.headers().add("Host", "testing-host:443");
		Request request = new Request(httpRequest, null);
		BaseUrl baseUrl = BaseUrlResolver.resolve(request);
		assertEquals("https", baseUrl.scheme);
		assertEquals("testing-host", baseUrl.host);
		assertEquals("443", baseUrl.port);
		assertEquals("https://testing-host", baseUrl.toString());
	}

	@Test
	public void shouldIgnoreHttpPort()
	{
		FullHttpRequest httpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/foo?param1=bar&param2=blah&yada");
		httpRequest.headers().add("Host", "testing-host");
		httpRequest.headers().add("Forwarded", "host=forwarded-host:80;proto=http");
		Request request = new Request(httpRequest, null);
		BaseUrl baseUrl = BaseUrlResolver.resolve(request);
		assertEquals("http", baseUrl.scheme);
		assertEquals("forwarded-host", baseUrl.host);
		assertEquals("80", baseUrl.port);
		assertEquals("http://forwarded-host", baseUrl.toString());
	}

	@Test
	public void shouldHonorForwardedFirst()
	{
		FullHttpRequest httpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/foo?param1=bar&param2=blah&yada");
		httpRequest.headers().add("Host", "testing-host");
		httpRequest.headers().add("Forwarded", "host=forwarded-host:80;proto=http");
		httpRequest.headers().add("X-Forwarded-Host", "example-host");
		httpRequest.headers().add("X-Forwarded-Proto", "https");
		httpRequest.headers().add("X-Forwarded-Port", "8443");
		Request request = new Request(httpRequest, null);
		BaseUrl baseUrl = BaseUrlResolver.resolve(request);
		assertEquals("http", baseUrl.scheme);
		assertEquals("forwarded-host", baseUrl.host);
		assertEquals("80", baseUrl.port);
		assertEquals("http://forwarded-host", baseUrl.toString());
	}

	@Test
	public void shouldHandleMultipleForwarded()
	{
		FullHttpRequest httpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/foo?param1=bar&param2=blah&yada");
		httpRequest.headers().add("Host", "testing-host");
		httpRequest.headers().add("Forwarded", "for=1.1.1.1;host=first.example;proto=http, for=2.2.2.2;host=second.example;proto=https");
		Request request = new Request(httpRequest, null);
		BaseUrl baseUrl = BaseUrlResolver.resolve(request);
		assertEquals("http", baseUrl.scheme);
		assertEquals("first.example", baseUrl.host);
		assertNull(baseUrl.port);
		assertEquals("http://first.example", baseUrl.toString());
	}

}
