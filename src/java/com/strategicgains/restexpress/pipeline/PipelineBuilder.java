/*
 * Copyright 2010, eCollege, Inc.  All rights reserved.
 */
package com.strategicgains.restexpress.pipeline;

import java.util.ArrayList;
import java.util.List;

import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpContentCompressor;
import org.jboss.netty.handler.codec.http.HttpContentDecompressor;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.jboss.netty.handler.stream.ChunkedWriteHandler;

/**
 * Provides a tiny DSL to define the pipeline features.
 * 
 * @author toddf
 * @since Aug 27, 2010
 */
public class PipelineBuilder
implements ChannelPipelineFactory
{
	// SECTION: CONSTANTS

	private static final int DEFAULT_MAX_CONTENT_LENGTH = 20480;


	// SECTION: INSTANCE VARIABLES

	private List<ChannelHandler> requestHandlers = new ArrayList<ChannelHandler>();
	private ExecutionHandler executionHandler = null;
	private int maxContentLength = DEFAULT_MAX_CONTENT_LENGTH;

	
	// SECTION: CONSTRUCTORS

	public PipelineBuilder()
	{
		super();
	}

	
	// SECTION: BUILDER METHODS
	
	public PipelineBuilder setExecutionHandler(ExecutionHandler handler)
	{
		this.executionHandler = handler;
		return this;
	}

	public PipelineBuilder addRequestHandler(ChannelHandler handler)
	{
		if (!requestHandlers.contains(handler))
		{
			requestHandlers.add(handler);
		}

		return this;
	}
	
	/**
	 * Set the maximum length of the aggregated (chunked) content. If the length of the
	 * aggregated content exceeds this value, a TooLongFrameException will be raised during
	 * the request, which can be mapped in the RestExpress server to return a
	 * BadRequestException, if desired.
	 * 
	 * @param value
	 * @return this PipelineBuilder for method chaining.
	 */
	public PipelineBuilder setMaxContentLength(int value)
	{
		this.maxContentLength = value;
		return this;
	}


	// SECTION: CHANNEL PIPELINE FACTORY

	@Override
	public ChannelPipeline getPipeline()
	throws Exception
	{
		ChannelPipeline pipeline = Channels.pipeline();

		pipeline.addLast("decoder", new HttpRequestDecoder());
		pipeline.addLast("aggregator", new HttpChunkAggregator(maxContentLength));
		pipeline.addLast("encoder", new HttpResponseEncoder());
		pipeline.addLast("chunkWriter", new ChunkedWriteHandler());
		pipeline.addLast("inflater", new HttpContentDecompressor());
		pipeline.addLast("deflater", new HttpContentCompressor());

		if (executionHandler != null)
		{
			pipeline.addLast("executionHandler", executionHandler);
		}

		for (ChannelHandler handler : requestHandlers)
		{
			pipeline.addLast(handler.getClass().getSimpleName(), handler);
		}

		return pipeline;
	}
}
