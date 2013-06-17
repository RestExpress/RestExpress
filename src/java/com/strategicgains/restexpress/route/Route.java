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
package com.strategicgains.restexpress.route;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.netty.handler.codec.http.HttpMethod;

import com.strategicgains.restexpress.Request;
import com.strategicgains.restexpress.Response;
import com.strategicgains.restexpress.exception.ServiceException;
import com.strategicgains.restexpress.url.UrlMatch;
import com.strategicgains.restexpress.url.UrlMatcher;
import com.strategicgains.restexpress.util.ParameterAssembler;


/**
 * A Route is an immutable relationship between a URL pattern and a REST
 * service.
 * 
 * @author toddf
 * @since May 4, 2010
 */
public abstract class Route
{
	// SECTION: INSTANCE VARIABLES

	private UrlMatcher urlMatcher;
	private Object controller;
	private Method action;
	private HttpMethod method;
	private boolean shouldSerializeResponse = true;
	private String name;
	private String baseUrl;
	private List<String> supportedFormats = new ArrayList<String>();
	private String defaultFormat;
	private Set<String> flags = new HashSet<String>();
	private Map<String, Object> parameters = new HashMap<String, Object>();

	// SECTION: CONSTRUCTORS

	/**
	 * @param urlMatcher
	 * @param controller
	 */
	public Route(UrlMatcher urlMatcher, Object controller, Method action, HttpMethod method, boolean shouldSerializeResponse,
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
	
	public boolean isFlagged(String flag)
	{
		return flags.contains(flag);
	}
	
	public boolean hasParameter(String name)
	{
		return (getParameter(name) != null);
	}

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
		return baseUrl;
	}

	public String getFullPattern()
	{
		return getBaseUrl() + getPattern();
	}

	public String getPattern()
	{
		return urlMatcher.getPattern();
	}
	
	public boolean shouldSerializeResponse()
	{
		return shouldSerializeResponse;
	}

    public Collection<String> getSupportedFormats()
    {
	    return Collections.unmodifiableList(supportedFormats);
    }
	
	public boolean hasSupportedFormats()
	{
		return (!supportedFormats.isEmpty());
	}
	
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

	public boolean supportsFormat(String format)
	{
		return supportedFormats.contains(format);
	}
	
	public String getDefaultFormat()
	{
		return defaultFormat;
	}
	
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
			List<Object> parameters = ParameterAssembler.assembleParameters(action, request, response);
			return action.invoke(controller, parameters.toArray());
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
