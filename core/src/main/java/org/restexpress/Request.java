/*
 * Copyright 2009, Strategic Gains, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.restexpress;

import java.io.File;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import org.restexpress.exception.BadRequestException;
import org.restexpress.pipeline.FileUploadHandler;
import org.restexpress.route.Route;
import org.restexpress.route.RouteResolver;
import org.restexpress.serialization.SerializationProvider;
import org.restexpress.serialization.SerializationSettings;
import org.restexpress.url.BaseUrlResolver;
import org.restexpress.url.QueryStringParser;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;

/**
 * @author toddf
 * @since Nov 20, 2009
 */
public class Request
{
	private static AtomicLong nextCorrelationId = new AtomicLong(0);


	// SECTION: INSTANCE VARIABLES

	private FullHttpRequest httpRequest;
	private HttpVersion httpVersion;
	private InetSocketAddress remoteAddress;
	private RouteResolver routeResolver;
	private SerializationProvider serializationProvider;
	private HttpMethod effectiveHttpMethod;
	private Route resolvedRoute;
	private String correlationId;
	private Map<String, Object> attachments;
	private Map<String, String> queryStringMap;

	private SerializationSettings serializationSettings;

	
	// SECTION: CONSTRUCTOR

	/**
	 * For testing only.
	 * 
	 * @param request
	 * @param routeResolver
	 */
	public Request(FullHttpRequest request, RouteResolver routeResolver)
	{
		this(request, routeResolver, null);
	}

	/**
	 * For testing only.
	 * 
	 * @param request
	 * @param routeResolver
	 * @param serializationProvider
	 */
	public Request(FullHttpRequest request, RouteResolver routeResolver, SerializationProvider serializationProvider)
	{
		super();
		this.httpRequest = request;
		this.httpVersion = request.protocolVersion();
		this.effectiveHttpMethod = request.method();
		this.routeResolver = routeResolver;
		this.serializationProvider = serializationProvider;
	    createCorrelationId();
        this.queryStringMap = parseQueryString(request);
		determineEffectiveHttpMethod(request);
	}

	public Request(InetSocketAddress socketAddress, FullHttpRequest request, RouteResolver routes, SerializationProvider serializationProvider)
	{
		this(request, routes, serializationProvider);
		this.remoteAddress = socketAddress;
	}


	// SECTION: ACCESSORS/MUTATORS

	/**
	 * Return the Correlation ID for this request.  The Correlation ID is unique for each request within
	 * this VM instance.  Restarting the VM will reset the correlation ID to zero.  It is not a GUID.
	 * It is useful, however, in correlating events in the pipeline (e.g. timing, etc.).  
	 */
	public String getCorrelationId()
	{
		return correlationId;
	}

	/**
	 * Return the HTTP method of the request.
	 * 
	 * @return HttpMethod of the request.
	 */
	public HttpMethod getHttpMethod()
	{
		return httpRequest.method();
	}
	
	/**
	 * Return the requested HTTP method of the request, whether via the request's HTTP method
	 * or via a query parameter (e.g. "_Method=").
	 * 
	 * @return the requested HttpMethod.
	 */
	public HttpMethod getEffectiveHttpMethod()
	{
		return effectiveHttpMethod;
	}

	public boolean isMethodGet()
	{
		return getEffectiveHttpMethod().equals(HttpMethod.GET);
	}

	public boolean isMethodDelete()
	{
		return getEffectiveHttpMethod().equals(HttpMethod.DELETE);
	}

	public boolean isMethodPost()
	{
		return getEffectiveHttpMethod().equals(HttpMethod.POST);
	}

	public boolean isMethodPut()
	{
		return getEffectiveHttpMethod().equals(HttpMethod.PUT);
	}

	public ByteBuf getBody()
    {
		return httpRequest.content();
    }

	/**
	 * Perform content-type negotiation and return the 
	 * best-match Media-Type as a string from this request.
	 * 
	 * @return the best-match media type.
	 */
	public String getMediaType()
	{
		return getSerializationSettings().getMediaType();
	}

	/**
	 * Attempts to deserialize the request body into an instance of the given type.
	 * 
	 * @param type the resulting type
	 * @return an instance of the requested type.
	 * @throws BadRequestException if the deserialization fails.
	 */
	public <T> T getBodyAs(Class<T> type)
	{
		return getSerializationSettings().deserialize(this, type);
	}

	/**
	 * Perform content-type negotiation for the request and return a
	 * corresponding {@link SerializationSettings} instance.
	 * 
	 * @return {@link SerializationSettings}. Never null.
	 */
	public SerializationSettings getSerializationSettings()
	{
		if (serializationSettings == null)
		{
			serializationSettings = serializationProvider.resolveRequest(this);
		}

		return serializationSettings;
	}

	/**
	 * Attempts to deserialize the request body into an instance of the given type.
	 * If the serialization process returns null, throws BadRequestException using
	 * the message.
	 * 
	 * @param type the resulting type.
	 * @param message the message for the BadRequestException if serialization returns null.
	 * @return an instance of the requested type.
	 * @throws BadRequestException if serialization fails.
	 */
	public <T> T getBodyAs(Class<T> type, String message)
	{
		T instance = getBodyAs(type);

		if (instance == null)
		{
			throw new BadRequestException(message);
		}
		
		return instance;
	}

	/**
	 * Returns the request body as an InputStream.
	 * 
	 * @return an InputStream
	 */
	public InputStream getBodyAsStream()
	{
		return new ByteBufInputStream(getBody());
	}

	/**
	 * Accesses the request body as a ByteBuffer.
	 * It is equivalent to calling getBody().toByteBuffer().
	 * 
	 * @return a ByteBuffer.
	 */
	public ByteBuffer getBodyAsByteBuffer()
	{
		return getBody().nioBuffer();
	}

	/**
	 * Returns the byte array underlying the Netty ByteBuf for this request.
	 * However, if the ByteBuf returns false to hasArray(), this
	 * method returns null.
	 * @author actolap All ByteBufs may not have backing array (i.e. direct memory)
	 * 
	 * @return an array of byte, or null, if the ByteBuf is not backed by a byte array.
	 */
	public byte[] getBodyAsBytes()
	{
		ByteBuf buf = getBody();
		int length = buf.readableBytes();
		byte[] bytes;

		if (buf.hasArray())
		{
			bytes = buf.array();
		}
		else
		{
			bytes = new byte[length];
			buf.getBytes(buf.readerIndex(), bytes);
		}

		return bytes;
	}

	/**
	 * Returns the body as a Map of name/value pairs from a url-form-encoded form submission.
	 * The values returned are URL-decoded.
	 * 
	 * @return
	 */
	public Map<String, List<String>> getBodyFromUrlFormEncoded()
	{
		return getBodyFromUrlFormEncoded(true);
	}

	/**
	 * Returns the body as a Map of name/value pairs from a url-form-encoded form submission.
	 * The values returned are URL-decoded depending on the value of shouldDecode.
	 * 
	 * @param shouldDecode true if the returned values should be URL-decoded
	 * @return
	 */
	public Map<String, List<String>> getBodyFromUrlFormEncoded(boolean shouldDecode)
	{
		if (shouldDecode)
		{
			QueryStringDecoder qsd = new QueryStringDecoder(getBody().toString(ContentType.CHARSET), ContentType.CHARSET, false);
			return qsd.parameters();
		}

		QueryStringParser qsp = new QueryStringParser(getBody().toString(ContentType.CHARSET), false);
		return qsp.getParameters();
	}

	public void setBody(ByteBuf body)
    {
		httpRequest.content().setBytes(0, body);
    }

	public void clearHeaders()
	{
		httpRequest.headers().clear();
	}

	/**
	 * Gets the named header from the request. Returns null if the header is not present.
	 * Both HTTP headers and query-string parameters are set as headers on the Request,
	 * with query-string parameters overriding headers if there is a name clash.
	 * <p/>
	 * NOTE: because HTTP headers are handled by Netty, which processes them with
	 *       QueryStringDecoder, HTTP headers are URL decoded. Also query-string
	 *       parameters that get processed by RestExpress are URL decoded before
	 *       being set as headers on the request.
	 * 
	 * @param name
	 * @return the requested header, or null if 'name' doesn't exist as a header.
	 */
	public String getHeader(String name)
	{
		if (queryStringMap.containsKey(name))
		{
			return queryStringMap.get(name);
		}

		return httpRequest.headers().get(name);
	}

	/**
	 * Gets the list of named headers from the request.
	 * Returns an empty list if the header name is not present.
	 * <p/>
	 * NOTE: because HTTP headers are handled by Netty, which processes them with
	 *       QueryStringDecoder, HTTP headers are URL decoded. Also query-string
	 *       parameters that get processed by RestExpress are URL decoded before
	 *       being set as headers on the request.
	 * 
	 * @param name
	 * @return the requested list of headers, or an empty list if 'name' doesn't exist as a header.
	 */
	public List<String> getHeaders(String name)
	{
		return Stream.concat(Stream.ofNullable(queryStringMap.get(name)),
			httpRequest.headers().getAll(name).stream()).toList();
	}
	
	/**
	 * Gets the named header from the request. Throws BadRequestException(message)
	 * if the header is not present.
	 * Both HTTP headers and query-string parameters are set as headers on the Request,
	 * with query-string parameters overriding headers if there is a name clash.
	 * <p/>
	 * NOTE: because HTTP headers are handled by Netty, which processes them with
	 *       QueryStringDecoder, HTTP headers are URL decoded. Also query-string
	 *       parameters that get processed by RestExpress are URL decoded before
	 *       being set as headers on the request.
	 * 
	 * @param name
	 * @return the requested header
	 * @throws BadRequestException if 'name' doesn't exist as a header.
	 */
	public String getHeader(String name, String message)
	{
		String value = getHeader(name);
		
		if (value == null)
		{
			throw new BadRequestException(message);
		}

		return value;
	}

	/**
	* Returns all header names in the request
	* 
	* @return Set of all header names
	*/
	public Set<String> getHeaderNames()
	{
		return httpRequest.headers().names();
	}
	
	/**
	* Adds a header to the request
	* 
	* Beware that headers set when parsing the HTTP request are indistinguishable
	* from those set programmatically via this method. Consider using {@link
	* #putAttachment} to persist data that should not be directly accessible by
	* the request.
	*/
	public void addHeader(String name, String value)
    {
		httpRequest.headers().add(name, value);
    }
	
	/**
	* Adds a collection of headers to the request
	* 
	* Beware that headers set when parsing the HTTP request are indistinguishable
	* from those set programmatically via this method. Consider using {@link
	* #putAttachment} to persist data that should not be directly accessible by
	* the request.
	*/
	public void addAllHeaders(Collection<Entry<String, String>> headers)
	{
    	for (Entry<String, String> entry : headers)
    	{
    		addHeader(entry.getKey(), entry.getValue());
    	}
	}

	public Route getResolvedRoute()
	{
		return resolvedRoute;
	}
	
	public void setResolvedRoute(Route route)
	{
		this.resolvedRoute = route;
	}

	/**
	 * Gets the path for this request.
	 * 
	 * @return
	 */
	public String getPath()
	{
		return httpRequest.uri();
	}
	
	/**
	 * Returns the protocol and host (without the path) portion of the URL.
	 * NOTE: Uses getScheme() which makes assumptions about the request
	 * protocol (http vs. https) based on the hostname.
	 * 
	 * If the request contains an X-Forwarded-Host header, it is used instead
	 * of host, enabling the use of load balancers and proxies while maintaining
	 * the anonymity of this service's IP address. 
	 * 
	 * @return
	 */
	public String getBaseUrl()
	{
		return BaseUrlResolver.resolve(this).toString();
	}

	public String getForwarded()
	{
		return httpRequest.headers().get("forwarded");
	}

	/**
	 * Returns the full URL for the request, containing protocol, host and path.
	 * Note that this call also returns the query string as part of the path.
	 * 
	 * @return the full URL for the request.
	 */
	public String getUrl()
	{
		return getBaseUrl() + getPath();
	}

	/**
	 * Get the named URL for the current effective HTTP method.
	 * 
	 * @param resourceName the name of the route
	 * @return the URL pattern, or null if the name/method does not exist.
	 */
	public String getNamedUrl(String resourceName)
	{
		return getNamedUrl(getEffectiveHttpMethod(), resourceName);
	}

	/**
	 * Get the named URL for the given HTTP method
	 * 
	 * @param method the HTTP method
	 * @param resourceName the name of the route
	 * @return the URL pattern, or null if the name/method does not exist.
	 */
	public String getNamedUrl(HttpMethod method, String resourceName)
	{
		Route route = routeResolver.getNamedRoute(resourceName, method);
		
		if (route != null)
		{
			return route.getFullPattern();
		}
		
		return null;
	}

	/**
	 * Get the path pattern (without protocol://host:port) for a named Route and given HTTP method
	 * 
	 * @param method the HTTP method
	 * @param resourceName the name of the route
	 * @return the URL Path pattern, or null if the name/method does not exist.
	 */
	public String getNamedPath(HttpMethod method, String resourceName)
	{
		Route route = routeResolver.getNamedRoute(resourceName, method);
		
		if (route != null)
		{
			return route.getPattern();
		}
		
		return null;
	}

	public Map<String, String> getQueryStringMap()
	{
		return queryStringMap;
	}

	public boolean isKeepAlive()
	{
		return HttpUtil.isKeepAlive(httpRequest);
	}

	public boolean isChunked()
	{
		if (HttpUtil.isTransferEncodingChunked(httpRequest))
			return true;
		else
        	return false;
	}
	
	/**
	 * Get the value of the {format} header in the request.
	 * 
	 * @return
	 * @deprecated .{format} on the URL is not recommended. Use Accept header instead.
	 */
	public String getFormat()
	{
		return getHeader(Parameters.Query.FORMAT);
	}
	
	/**
	 * Get the host (and port) from the request.
	 * 
	 * @return
	 */
	public String getHost()
	{
		return httpRequest.headers().get(HttpHeaderNames.HOST);
	}

	/**
	 * Get the X-Forwarded-Host header from the request.
	 * 
	 * @return the X-Forwarded-Host header (as a string) or null if not present on the request.
	 */
	public String getXForwardedHost()
	{
		return httpRequest.headers().get("X-Forwarded-Host");
	}

	/**
	 * Get the protocol of the request.
	 * Note: Netty doesn't report this correctly--it will ALWAYS be 'http'
	 * 
	 * @return "http" in lower-case.
	 * @deprecated Use getScheme() which makes some attempt at figuring out https or http.
	 */
	public String getProtocol()
	{
		
		return httpRequest.protocolVersion().protocolName().toLowerCase();
	}

	/**
	 * Get the protocol of the request, making some assumptions based on the host.
	 * 
	 * Hosts of localhost and 127.0.0.1 return 'http'. All others return 'https'. 
	 * 
	 * @return "http" (for localhost and 127.0.01) or "https" for other hosts, in lower-case.
	 */
	public String getScheme()
	{
		String host = getHost();

		if (host == null || host.startsWith("localhost") || host.startsWith("127.0.0.1"))
		{
			return "http";
		}

		return "https";
	}
	
	/**
	 * Checks the format request parameter against the given format value.
	 * Ignores case.
	 * 
	 * @param format
	 * @return true if the given format matches (case insensitive) the request format parameter. Otherwise false.
	 */
	public boolean isFormatEqual(String format)
	{
		return isHeaderEqual(Parameters.Query.FORMAT, format);
	}
	
	/**
	 * Checks the value of the given header against the given value.
	 * Ignores case.  If the header value or given value is null or
	 * has a trimmed length of zero, returns false.
	 * 
	 * @param name the name of a header to check.
	 * @param value the expected value.
	 * @return true if the header equals (ignoring case) to the given value.
	 */
	public boolean isHeaderEqual(String name, String value)
	{
		String header = getHeader(name);

		if (header == null || header.isEmpty() || value == null || value.isEmpty()) return false;
		
		return header.trim().equalsIgnoreCase(value.trim());
	}
	
	/**
	 * Ask if the request contains the named flag.  Flags are boolean settings that are created at route definition time.
	 * These flags can be used to pass booleans to preprocessors, controllers, or postprocessors.  An example might be:
	 * flag(NO_AUTHORIZATION), which might inform an authorization preprocessor to skip authorization for this route.
	 * 
	 * @param flag the name of a flag.
	 * @return true if the request contains the named flag, otherwise false.
	 */
	public boolean isFlagged(String flag)
	{
		return resolvedRoute.isFlagged(flag);
	}
	
	/**
	 * Get a named parameter.  Parameters are named settings that are created at route definition time. These parameters
	 * can be used to pass data to subsequent preprocessors, controllers, or postprocessors.  This is a way to pass data
	 * from a route definition down to subsequent controllers, etc.  An example might be: setParameter("route", "read_foo")
	 * setParameter("permission", "view_private_data"), which might inform an authorization preprocessor of what permission
	 * is being requested on a given resource.
	 * 
	 * @param name the name of a parameter to retrieve.
	 * @return the named parameter or null, if not present.
	 */
	public Object getParameter(String name)
	{
		return resolvedRoute.getParameter(name);
	}
	
	/**
	 * Each request can have many user-defined attachments, perhaps placed via preprocessors, etc.
	 * These attachments are named and are carried along with the request to subsequent preprocessors,
	 * controllers, and postprocessors.  Attachments are different than parameters in that, they are set
	 * on a per request basis, instead of at the route level.  They can be set via preprocessors, controllers,
	 * postprocessor, as opposed to parameters which are set on the route definition.
	 * 
	 * @param name the name of an attachment.
	 * @return the named attachment, or null if it is not present.
	 */
	public Object getAttachment(String name)
	{
		if (attachments != null)
		{
			return attachments.get(name);
		}
		
		return null;
	}

	public File getUploadedFileAttachment()
	{
		String filePath = (String) getAttachment(FileUploadHandler.FILE_ATTACHMENT_KEY);
		return (filePath != null ? new File(filePath) : null);
	}

	/**
	 * Determine whether a named attachment is present.
	 * 
	 * @param name the name of a parameter.
	 * @return true if the parameter is present, otherwise false.
	 */
	public boolean hasAttachment(String name)
	{
		return (getAttachment(name) != null);
	}
	
	/**
	 * Set an attachment on this request.
	 * These attachments are named and are carried along with the request to subsequent preprocessors,
	 * controllers, and postprocessors.  Attachments are different than parameters in that, they are set
	 * on a per request basis, instead of at the route level.  They can be set via preprocessors, controllers,
	 * postprocessor, as opposed to parameters which are set on the route definition.
	 * 
	 * @param name the name of the attachment.
	 * @param attachment the attachment to associate with this request.
	 */
	public void putAttachment(String name, Object attachment)
	{
		if (attachments == null)
		{
			attachments = new HashMap<>();
		}
		
		attachments.put(name, attachment);
	}
	
	public HttpVersion getHttpVersion()
	{
		return httpVersion;
	}
	
	public boolean isHttpVersion1_0()
	{
		return ((httpVersion.majorVersion() == 1) && (httpVersion.minorVersion() == 0));
	}
	
	public InetSocketAddress getRemoteAddress()
	{
		return remoteAddress;
	}

	
	// SECTION: UTILITY - PRIVATE

	/**
	 * Add the query string parameters to the request as headers.
	 * Also parses the query string into the queryStringMap, if applicable. Note, if the query string
	 * contains multiple of the same parameter name, the headers will contain them all, but the
	 * queryStringMap will only contain the first one.  This will be fixed in a future release.
	 */
	private Map<String, String> parseQueryString(final HttpRequest request)
	{
		if (!request.uri().contains("?")) return Collections.emptyMap();

		Map<String, List<String>> parameters = decodeQueryString(request.uri());

		if (parameters == null || parameters.isEmpty()) return Collections.emptyMap();

		Map<String, String> parameterMap = HashMap.newHashMap(parameters.size());
		
		for (Entry<String, List<String>> entry : parameters.entrySet())
		{
			String key = decode(entry.getKey());
			parameterMap.put(key, entry.getValue().get(0));
		}

		return parameterMap;
	}

	private Map<String, List<String>> decodeQueryString(String uri)
	{
		try
		{
			return new QueryStringDecoder(uri).parameters();
		}
		catch (Exception e)
		{
			// Fallback for improperly encoded URIs
			if (uri.contains("?"))
			{
				String rawQuery = uri.substring(uri.indexOf('?') + 1);
				String encodedQuery = encodeQueryParameters(rawQuery);
				return new QueryStringDecoder("?" + encodedQuery).parameters();
			}

			return Collections.emptyMap();
		}
	}

	private String encodeQueryParameters(String rawQuery)
	{
		String[] pairs = rawQuery.split("&");
		StringBuilder encoded = new StringBuilder();

		for (int i = 0; i < pairs.length; i++)
		{
			String[] keyValue = pairs[i].split("=", 2);
			if (keyValue.length == 2)
			{
				encoded.append(encode(keyValue[0])).append("=").append(encode(keyValue[1]));
			}
			else
			{
				encoded.append(encode(keyValue[0]));
			}

			if (i < pairs.length - 1)
			{
				encoded.append("&");
			}
		}

		return encoded.toString();
	}

	private String decode(String encoded)
	{
		try
		{
			return URLDecoder.decode(encoded, ContentType.ENCODING);
		}
		catch (Exception e)
		{
			return encoded;
		}
	}

	private String encode(String decoded)
	{
		try
		{
			return URLEncoder.encode(decoded, ContentType.ENCODING);
		}
		catch (Exception e)
		{
			return decoded;
		}
	}

	/**
	 * If the request HTTP method is post, allow a query string parameter to determine
	 * the request HTTP method of the post (e.g. _method=DELETE or _method=PUT).  This
	 * supports DELETE and PUT from the browser.
	 */
	private void determineEffectiveHttpMethod(HttpRequest request)
	{
		if (!HttpMethod.POST.equals(request.method())) return;

		String methodString = getQueryStringMap().get(Parameters.Query.METHOD_TUNNEL);

		if ("PUT".equalsIgnoreCase(methodString) || "DELETE".equalsIgnoreCase(methodString))
		{
			effectiveHttpMethod = HttpMethod.valueOf(methodString.toUpperCase());
		}
	}
	
	private void createCorrelationId()
	{
		this.correlationId = String.valueOf(nextCorrelationId.incrementAndGet());
	}
}
