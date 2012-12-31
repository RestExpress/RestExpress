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

package com.strategicgains.restexpress;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.HashSet;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.codec.http.QueryStringDecoder;

import com.strategicgains.restexpress.exception.BadRequestException;
import com.strategicgains.restexpress.exception.ServiceException;
import com.strategicgains.restexpress.route.Route;
import com.strategicgains.restexpress.route.RouteResolver;
import com.strategicgains.restexpress.serialization.SerializationProcessor;
import com.strategicgains.restexpress.url.QueryStringParser;

/**
 * @author toddf
 * @since Nov 20, 2009
 */
public class Request
{
	// SECTION: CONSTANTS

	private static final String DEFAULT_PROTOCOL = "http";
	
	private static long nextCorrelationId = 0;


	// SECTION: INSTANCE VARIABLES

	private HttpRequest httpRequest;
	private HttpVersion httpVersion;
	private SerializationProcessor serializationProcessor;
	private RouteResolver urlRouter;
	private HttpMethod effectiveHttpMethod;
	private Route resolvedRoute;
	private String correlationId;
	private Map<String, Object> attachments;
	private Map<String, String> queryStringMap;

	
	// SECTION: CONSTRUCTOR

	public Request(HttpRequest request, RouteResolver routes)
	{
		super();
		this.httpRequest = request;
		this.httpVersion = request.getProtocolVersion();
		this.effectiveHttpMethod = request.getMethod();
		this.urlRouter = routes;
		initialize();
	}

	private void initialize()
    {
	    createCorrelationId();
//		parseRequestedFormatToHeader(httpRequest);
		parseQueryString(httpRequest);
		determineEffectiveHttpMethod(httpRequest);
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
		return httpRequest.getMethod();
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

	public ChannelBuffer getBody()
    {
		return httpRequest.getContent();
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
		try
		{
			return serializationProcessor.deserialize(getBody(), type);
		}
		catch(Exception e)
		{
			throw new BadRequestException(e);
		}
	}

	/**
	 * Attempts to deserialize the request body into an instance of the given type.
	 * If the serialization process returns null, throws BadResquestExcption using
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
	 * Returns the body as a Map of name/value pairs from a url-form-encoded form submission.
	 * 
	 * @return
	 */
	public Map<String, List<String>> getBodyFromUrlFormEncoded()
	{
		QueryStringDecoder qsd = new QueryStringDecoder(getBody().toString(ContentType.CHARSET), ContentType.CHARSET, false);
		return qsd.getParameters();
	}

	public SerializationProcessor getSerializationProcessor()
    {
    	return serializationProcessor;
    }

	public void setSerializationProcessor(SerializationProcessor serializationProcessor)
    {
    	this.serializationProcessor = serializationProcessor;
    }

	public void setBody(ChannelBuffer body)
    {
		httpRequest.setContent(body);
    }

	public void clearHeaders()
	{
		httpRequest.clearHeaders();
	}
	
	/**
	 * Gets the named header as it came in on the request (without URL decoding it).
	 * Returns null if the header is not present.
	 * <p/>
	 * NOTE: because HTTP headers are handled by Netty, which processes them with
	 *       QueryStringDecoder, HTTP headers are URL decoded. Only query-string
	 *       parameters that get processed by RestExpress are NOT URL decoded.
	 * 
	 * @param name
	 * @return the requested header, or null if 'name' doesn't exist as a header.
	 */
	public String getRawHeader(String name)
	{
		return httpRequest.getHeader(name);
	}
	
	/**
	 * Gets the named header as it came in on the request (without URL decoding it).
	 * Throws BadRequestException(message) if the header is not present.
	 * 
	 * NOTE: because HTTP headers are handled by Netty, which processes them with
	 *       QueryStringDecoder, HTTP headers are URL decoded. Only query-string
	 *       parameters that get processed by RestExpress are NOT URL decoded.
	 * 
	 * @param name
	 * @return the requested header
	 * @throws BadRequestException(message) if 'name' doesn't exist as a header.
	 */
	public String getRawHeader(String name, String message)
	{
		String value = getRawHeader(name);
		
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
		return httpRequest.getHeaderNames();
	}

	/**
	 * Gets the named header, URL decoding it before returning it.
	 * Returns null if the header is not present.
	 * 
	 * @param name
	 * @return the requested header, or null if 'name' doesn't exist as a header.
	 */
	public String getUrlDecodedHeader(String name)
	{
		String value = httpRequest.getHeader(name);
		return (value != null ? urlDecode(value) : null);
	}
	
	/**
	 * Gets the named header, URL decoding it before returning it.
	 * Throws BadRequestException(message) if the header is not present.
	 * 
	 * @return the requested header
	 * @throws BadRequestException(message) if 'name' doesn't exist as a header.
	 */
	public String getUrlDecodedHeader(String name, String message)
	{
		String value = getUrlDecodedHeader(name);
		
		if (value == null)
		{
			throw new BadRequestException(message);
		}

		return value;
	}
	
	public void addHeader(String name, String value)
    {
		httpRequest.addHeader(name, value);
    }
	
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
		return httpRequest.getUri();
	}
	
	/**
	 * Returns the protocol and host (without the path) portion of the URL.
	 * 
	 * @return
	 */
	public String getBaseUrl()
	{
		return getProtocol() + "://" + getHost();
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
		Route route = urlRouter.getNamedRoute(resourceName, method);
		
		if (route != null)
		{
			return route.getFullPattern();
		}
		
		return null;
	}
	
	public Map<String, String> getQueryStringMap()
	{
		return queryStringMap;
	}

	public boolean isKeepAlive()
	{
		return HttpHeaders.isKeepAlive(httpRequest);
	}

	public boolean isChunked()
	{
		return httpRequest.isChunked();
	}
	
	/**
	 * Get the value of the {format} header in the request.
	 * 
	 * @return
	 */
	public String getFormat()
	{
		return getRawHeader(Parameters.Query.FORMAT);
	}
	
	/**
	 * Get the host (and port) from the request.
	 * 
	 * @return
	 */
	public String getHost()
	{
		return HttpHeaders.getHost(httpRequest);
	}

	/**
	 * Get the protocol of the request.  RESTExpress currently only supports 'http'
	 * and will always return that value.
	 * 
	 * @return "http"
	 */
	public String getProtocol()
	{
		return DEFAULT_PROTOCOL;
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
	 * Ignores case and attempts to URLDecode the header.  If the header
	 * value or given value is null or has a trimmed length of zero, returns false.
	 * 
	 * @param name the name of a header to check.
	 * @param value the expected value.
	 * @return true if the header equals (ignoring case) to the given value.
	 */
	public boolean isHeaderEqual(String name, String value)
	{
		String header = getUrlDecodedHeader(name);

		if (header == null || header.trim().length() == 0 || value == null || value.trim().length() == 0)
			return false;
		
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
			attachments = new HashMap<String, Object>();
		}
		
		attachments.put(name, attachment);
	}
	
	public HttpVersion getHttpVersion()
	{
		return httpVersion;
	}
	
	public boolean isHttpVersion1_0()
	{
		return ((httpVersion.getMajorVersion() == 1) && (httpVersion.getMinorVersion() == 0));
	}

	
	// SECTION: UTILITY - PRIVATE

	/**
	 * Add the query string parameters to the request as headers.
	 * Also parses the query string into the queryStringMap, if applicable. Note, if the query string
	 * contains multiple of the same parameter name, the headers will contain them all, but the
	 * queryStringMap will only contain the first one.  This will be fixed in a future release.
	 */
	private void parseQueryString(final HttpRequest request)
	{
		if (!request.getUri().contains("?")) return;

		Map<String, List<String>> parameters = new QueryStringParser(request.getUri(), true).getParameters();

		if (parameters == null || parameters.isEmpty()) return;

		queryStringMap = new HashMap<String, String>(parameters.size());
		
		for (Entry<String, List<String>> entry : parameters.entrySet())
		{
			queryStringMap.put(entry.getKey(), entry.getValue().get(0));

			for (String value : entry.getValue())
			{
				request.addHeader(entry.getKey(), value);
			}
		}
	}

	/**
	 * If the request HTTP method is post, allow a query string parameter to determine
	 * the request HTTP method of the post (e.g. _method=DELETE or _method=PUT).  This
	 * supports DELETE and PUT from the browser.
	 * 
	 * @param parameters
	 */
	private void determineEffectiveHttpMethod(HttpRequest request)
	{
		if (!HttpMethod.POST.equals(request.getMethod())) return;

		String methodString = request.getHeader(Parameters.Query.METHOD_TUNNEL);

		if ("PUT".equalsIgnoreCase(methodString) || "DELETE".equalsIgnoreCase(methodString))
		{
			effectiveHttpMethod = HttpMethod.valueOf(methodString.toUpperCase());
		}
	}
	
	private void createCorrelationId()
	{
		this.correlationId = String.valueOf(++nextCorrelationId);
	}

	private String urlDecode(String value)
	{
        try
        {
	        return URLDecoder.decode(value, ContentType.ENCODING);
        }
        catch (UnsupportedEncodingException e)
        {
        	throw new ServiceException(e);
        }
        catch(IllegalArgumentException iae)
        {
        	throw new BadRequestException(iae);
        }
	}
}
