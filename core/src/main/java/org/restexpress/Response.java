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

import static io.netty.handler.codec.http.HttpResponseStatus.OK;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.restexpress.common.query.QueryRange;
import org.restexpress.serialization.SerializationSettings;

/**
 * @author toddf
 * @since Nov 20, 2009
 */
public class Response
{
	// SECTION: INSTANCE VARIABLES

	private HttpResponseStatus responseCode = OK;
	private Object body;
	private Map<String, List<String>> headers = new HashMap<>();
	private boolean isSerialized = true;
	private Throwable exception = null;
	private SerializationSettings serializationSettings;
	
	// SECTION: CONSTRUCTORS

	public Response()
	{
		super();
	}


	// SECTION: ACCESSORS/MUTATORS

	public Object getBody()
	{
		return body;
	}

	public boolean hasBody()
	{
		return (getBody() != null);
	}

	public void setBody(Object body)
	{
		this.body = body;
	}

	public void clearHeaders()
	{
		headers.clear();
	}

	public String getHeader(String name)
	{
		List<String> list = headers.get(name);

		if (list != null && !list.isEmpty())
		{
			return list.get(0);
		}
		
		return null;
	}

	public List<String> getHeaders(String name)
	{
		return headers.get(name);
	}
	
	public boolean hasHeader(String name)
	{
		return (getHeader(name) != null);
	}

	public boolean hasHeaders()
	{
		return !headers.isEmpty();
	}

	public Set<String> getHeaderNames()
	{
		return headers.keySet();
	}

	/**
	 * Add a header value to the response.
	 * 
	 * @param name
	 * @param value
	 */
	public void addHeader(String name, String value)
	{
		List<String> list = headers.get(name);
		
		if (list == null)
		{
			list = new ArrayList<>();
			headers.put(name, list);
		}
		
		list.add(value);
	}

	/**
	 * Add a "Content-Range" header to the response, setting it to the range and count.
	 * This enables datagrid-style pagination support.
	 * 
	 * @param range
	 * @param count
	 */
	public void addRangeHeader(QueryRange range, long count)
	{
    	addHeader(HttpHeaderNames.CONTENT_RANGE.toString(), range.asContentRange(count));
	}
	
	public void addLocationHeader(String url)
	{
		addHeader(HttpHeaderNames.LOCATION.toString(), url);
	}

	/**
	 * Sets HTTP response code and Content-Range header appropriately for
	 * the requested QueryRange, returned collection size and maximum data set size.
	 * 
	 * @param queryRange
	 * @param size
	 * @param count
	 */
	public void setCollectionResponse(QueryRange queryRange, int size, long count)
	{
		QueryRange range = queryRange.clone();

		if (count < 0)
		{
			addRangeHeader(range, count);
			return;
		}

		if (range.isOutside(size, count))
		{
			setResponseCode(416);
			range.setOffset(0);
			range.setLimitViaEnd(Math.min(count - 1, range.getLimit()));
		}
		else if (range.extendsBeyond(size, count))
		{
			if (count == 0 && range.getOffset() > 0)
			{
				setResponseCode(416);
				range.setOffset(0);
				range.setLimitViaEnd(0);
			}
			else
			{
				range.setLimitViaEnd((count > 1 ? count - 1 : 0));
			}
			
			if (count > 0 && !range.spans(size, count))
			{
				setResponseCode(206);
			}
		}
		else if (range.isInside(size, count))
		{
			setResponseCode(206);
		}

		addRangeHeader(range, count);
	}

	/**
	 * Set the HTTP response status code.
	 * 
	 * @param value
	 */
	public void setResponseCode(int value)
	{
		setResponseStatus(HttpResponseStatus.valueOf(value));
	}
	
	/**
	 * Set the HTTP response status.
	 * 
	 * @param status
	 */
	public void setResponseStatus(HttpResponseStatus status)
	{
		this.responseCode = status;
	}
	
	/**
	 * Sets the HTTP response status code to 201 - created.
	 */
	public void setResponseCreated()
	{
		setResponseStatus(HttpResponseStatus.CREATED);
	}
	
	/**
	 * Sets the HTTP response status code to 204 - no content.
	 * Note, however, if a wrapped response is requested, then
	 * this method has no effect (as the body will contain content).
	 */
	public void setResponseNoContent()
	{
		// TODO: fix this...
//		if (!responseProcessor.getWrapper().addsBodyContent(this))
//		{
			setResponseStatus(HttpResponseStatus.NO_CONTENT);
//		}
	}
	
	/**
	 * Get the HTTP Response Status.
	 * 
	 * @return
	 */
	public HttpResponseStatus getResponseStatus()
	{
		return responseCode;
	}

	public String getContentType()
    {
		return getHeader(HttpHeaderNames.CONTENT_TYPE.toString());
    }

	public void setContentType(String contentType)
    {
		List<String> list = headers.get(HttpHeaderNames.CONTENT_TYPE);

		if (list != null && !list.isEmpty())
		{
			list.clear();
			list.add(contentType);
		}
		else if (list == null)
		{
			addHeader(HttpHeaderNames.CONTENT_TYPE.toString(), contentType);
		}
    }

	public boolean isSerialized()
	{
		return isSerialized;
	}
	
	public void setIsSerialized(boolean value)
	{
		this.isSerialized = value;
	}

	public void noSerialization()
	{
		setIsSerialized(false);
	}
	
	public void useSerialization()
	{
		setIsSerialized(true);
	}

	public Throwable getException()
    {
    	return exception;
    }
	
	public boolean hasException()
	{
		return (getException() != null);
	}

	public void setException(Throwable exception)
    {
    	this.exception = exception;
    }
	
	public void setSerializationSettings(SerializationSettings settings)
	{
		this.serializationSettings = settings;
	}
	
	public boolean hasSerializationSettings()
	{
		return (serializationSettings != null);
	}

	/**
	 * Return the best-match Content-Type for this response.
	 * If an error has occurred before content-type negotiation has occurred,
	 * returns null.
	 * 
	 * @return the best-match Content-Type using content-type negotiation. Possibly null.
	 */
	public String getMediaType()
	{
		return (hasSerializationSettings() ? serializationSettings.getMediaType() : null);
	}

	public SerializationSettings getSerializationSettings()
	{
		return serializationSettings;
	}
}
