/*
 * Copyright 2010, Strategic Gains, Inc.
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

package com.strategicgains.restexpress.serialization.xml;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferInputStream;

import com.strategicgains.restexpress.ContentType;
import com.strategicgains.restexpress.domain.Link;
import com.strategicgains.restexpress.domain.ResultWrapper;
import com.strategicgains.restexpress.serialization.AliasingSerializationProcessor;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.SingleValueConverter;

/**
 * @author toddf
 * @since Mar 16, 2010
 */
public class DefaultXmlProcessor
implements AliasingSerializationProcessor
{
	private XStream xstream;
	private Map<Class<?>, String> aliases = new HashMap<Class<?>, String>();
	private boolean shouldAutoAlias = true;
	
	public DefaultXmlProcessor()
	{
		this(new XStream());
		xstream.registerConverter(new XstreamTimestampConverter());
		xstream.alias("link", Link.class);
		xstream.alias("list", Collections.EMPTY_LIST.getClass());
		xstream.alias("response", ResultWrapper.class);
	}
	
	public DefaultXmlProcessor(XStream xstream)
	{
		super();
		this.xstream = xstream;
		shouldAutoAlias = false;
	}
	
	protected XStream getXStream()
	{
		return this.xstream;
	}
	
	
	// SECTION: XML NAME ALIASING

	@Override
	public void alias(String name, Class<?> type)
	{
		xstream.alias(name, type);
	}
	
	public void registerConverter(SingleValueConverter converter)
	{
		xstream.registerConverter(converter);
	}


	// SECTION: SERIALIZATION PROCESSOR

	@Override
	public String serialize(Object object)
	{
		return xstream.toXML(object);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T deserialize(String xml, Class<T> type)
	{
		if (shouldAutoAlias)
		{
			addAliasIfNecessary(type);
		}

		return (T) xstream.fromXML(xml);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T deserialize(ChannelBuffer xml, Class<T> type)
	{
		return (T) xstream.fromXML(new ChannelBufferInputStream(xml));
	}

	@Override
	public String getResultingContentType()
	{
		return ContentType.XML;
	}

	private void addAliasIfNecessary(Class<?> type)
	{
		if (!aliases.containsKey(type))
		{
			String name = type.getSimpleName().trim();
			
			if ("[]".equals(name) || "".equals(name))
			{
				return;
			}
			
			xstream.alias(name, type);
		}
	}
}
