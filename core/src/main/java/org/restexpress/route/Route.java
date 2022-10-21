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
package org.restexpress.route;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.common.util.StringUtils;
import org.restexpress.contenttype.MediaRange;
import org.restexpress.exception.ServiceException;
import org.restexpress.url.UrlMatch;
import org.restexpress.url.UrlMatcher;

import io.netty.handler.codec.http.HttpMethod;


/**
 * A Route is an immutable relationship between a URL pattern and a REST
 * controller.
 * 
 * @author toddf
 * @since May 4, 2010
 */
public abstract class Route
{
	private UrlMatcher urlMatcher;
	private Object controller;
	private Method action;
	private HttpMethod method;
	private List<MediaRange> contentTypes;
	private boolean shouldSerializeResponse = true;
	private String name;
	private String baseUrl;
	private List<String> supportedFormats = new ArrayList<String>();
	private String defaultFormat;
	private Set<String> flags = new HashSet<String>();
	private Map<String, Object> parameters = new HashMap<String, Object>();

	/**
	 * @param urlMatcher
	 * @param controller
	 */
	protected Route(UrlMatcher urlMatcher, Object controller, Method action, HttpMethod method, boolean shouldSerializeResponse,
		String name, List<String> supportedFormats, String defaultFormat, Set<String> flags, Map<String, Object> parameters,
		String baseUrl)
	{
		super();
		this.urlMatcher = urlMatcher;
		this.controller = controller;
		this.action = action;
		this.action.setAccessible(true);
		this.method = method;
		this.shouldSerializeResponse = shouldSerializeResponse;
		this.name = name;
		this.supportedFormats.addAll(supportedFormats);
		this.defaultFormat = defaultFormat;
		this.flags.addAll(flags);
		this.parameters.putAll(parameters);
		this.baseUrl = baseUrl;
	}

	/**
	 * Answer whether the route contains the given flag.
	 * 
	 * @param flag
	 * @return true if the route contains the given flag.
	 */
	public boolean isFlagged(String flag)
	{
		if (flag == null) return false;

		return flags.contains(flag);
	}

	/**
	 * Answer whether the route contains all the given flags.
	 * 
	 * @param flags
	 * @return true if the route contains all the given flags.
	 */
	public boolean containsAllFlags(String[] flags)
	{
		if (flags == null) return false;

		return this.flags.containsAll(Arrays.asList(flags));
	}

	/**
	 * Answer whether the route contains any of the given flags.
	 * 
	 * @param flags
	 * @return true if the route contains any of the given flags.
	 */
	public boolean containsAnyFlags(String[] flags)
	{
		if (flags == null) return false;

		for (String flag : flags)
		{
			if (isFlagged(flag))
			{
				return true;
			}
		}

		return false;
	}

	/**
	 * Answer whether the route contains the given parameter.
	 * 
	 * @param name
	 * @return true if the route contains the given parameter. Otherwise false.
	 */
	public boolean hasParameter(String name)
	{
		return (getParameter(name) != null);
	}

	/**
	 * Retrieve a parameter value by name from the route.
	 * 
	 * @param name
	 * @return the parameter value or null if the name is not found.
	 */
	public Object getParameter(String name)
	{
		return parameters.get(name);
	}

	public Method getAction()
	{
		return action;
	}
	
	public Object getController()
	{
		return controller;
	}
	
	public HttpMethod getMethod()
	{
		return method;
	}
	
	public String getName()
	{
		return name;
	}
	
	public boolean hasName()
	{
		return (getName() != null && !getName().trim().isEmpty());
	}

	public String getBaseUrl()
	{
		return (baseUrl == null ? StringUtils.EMPTY_STRING : baseUrl);
	}

	/**
	 * Returns the base URL + the URL pattern.  Useful in creating links.
	 * 
	 * @return a string URL pattern containing the base URL.
	 */
	public String getFullPattern()
	{
		return getBaseUrl() + getPattern();
	}

	/**
	 * Returns the URL pattern without any '.{format}' at the end.  In essence, a 'short' URL pattern.
	 * 
	 * @return a URL pattern
	 */
	public String getPattern()
	{
		return urlMatcher.getPattern();
	}

	public boolean appliesTo(HttpMethod method, MediaRange contentType)
	{
		return (this.method.equals(method) && (contentTypes == null || contentTypes.contains(contentType)));
	}

	public boolean shouldSerializeResponse()
	{
		return shouldSerializeResponse;
	}

	/**
	 * @deprecated
	 */
    public Collection<String> getSupportedFormats()
    {
	    return Collections.unmodifiableList(supportedFormats);
    }
	
	/**
	 * @deprecated
	 */
	public boolean hasSupportedFormats()
	{
		return (!supportedFormats.isEmpty());
	}
	
	/**
	 * @deprecated
	 */
	public void addAllSupportedFormats(List<String> formats)
	{
		supportedFormats.addAll(formats);
	}
	
	public void addSupportedFormat(String format)
	{
		if (!supportsFormat(format))
		{
			supportedFormats.add(format);
		}
	}

	/**
	 * @deprecated
	 */
	public boolean supportsFormat(String format)
	{
		return supportedFormats.contains(format);
	}
	
	/**
	 * @deprecated
	 */
	public String getDefaultFormat()
	{
		return defaultFormat;
	}
	
	/**
	 * @deprecated
	 */
	public boolean hasDefaultFormat()
	{
		return defaultFormat != null;
	}

	public UrlMatch match(String url)
	{
		return urlMatcher.match(url);
	}
	
	public List<String> getUrlParameters()
	{
		return urlMatcher.getParameterNames();
	}

	public Object invoke(Request request, Response response)
	{
		try
        {
	        return action.invoke(controller, request, response);
        }
		catch (InvocationTargetException e)
		{
			Throwable cause = e.getCause();
			
			if (RuntimeException.class.isAssignableFrom(cause.getClass()))
			{
				throw (RuntimeException) e.getCause();
			}
			else
			{
				throw new RuntimeException(cause);
			}
		}
        catch (Exception e)
        {
        	throw new ServiceException(e);
        }
	}
}
