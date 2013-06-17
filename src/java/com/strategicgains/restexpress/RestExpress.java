/*
 * Copyright 2009-2012, Strategic Gains, Inc.
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

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.ChannelGroupFuture;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;

import com.strategicgains.restexpress.domain.metadata.ServerMetadata;
import com.strategicgains.restexpress.exception.ExceptionMapping;
import com.strategicgains.restexpress.exception.ServiceException;
import com.strategicgains.restexpress.pipeline.DefaultRequestHandler;
import com.strategicgains.restexpress.pipeline.MessageObserver;
import com.strategicgains.restexpress.pipeline.PipelineBuilder;
import com.strategicgains.restexpress.pipeline.Postprocessor;
import com.strategicgains.restexpress.pipeline.Preprocessor;
import com.strategicgains.restexpress.plugin.Plugin;
import com.strategicgains.restexpress.response.ResponseProcessor;
import com.strategicgains.restexpress.response.ResponseProcessorResolver;
import com.strategicgains.restexpress.route.RouteBuilder;
import com.strategicgains.restexpress.route.RouteDeclaration;
import com.strategicgains.restexpress.route.RouteResolver;
import com.strategicgains.restexpress.route.parameterized.ParameterizedRouteBuilder;
import com.strategicgains.restexpress.route.regex.RegexRouteBuilder;
import com.strategicgains.restexpress.serialization.AliasingSerializationProcessor;
import com.strategicgains.restexpress.settings.RouteDefaults;
import com.strategicgains.restexpress.settings.ServerSettings;
import com.strategicgains.restexpress.settings.SocketSettings;
import com.strategicgains.restexpress.util.Bootstraps;
import com.strategicgains.restexpress.util.Callback;
import com.strategicgains.restexpress.util.DefaultShutdownHook;
import com.strategicgains.restexpress.util.Resolver;

/**
 * Primary entry point to create a RestExpress service. All that's required is a
 * RouteDeclaration. By default: port is 8081, serialization format is JSON,
 * supported formats are JSON and XML.
 * 
 * @author toddf
 */
public class RestExpress
{
	private static final ChannelGroup allChannels = new DefaultChannelGroup("RestExpress");

	public static final String DEFAULT_NAME = "RestExpress";
	public static final int DEFAULT_PORT = 8081;

	private ServerBootstrap bootstrap;
	private SocketSettings socketSettings = new SocketSettings();
	private ServerSettings serverSettings = new ServerSettings();
	private RouteDefaults routeDefaults = new RouteDefaults();
	private boolean useSystemOut;

	Map<String, ResponseProcessor> responseProcessors = new HashMap<String, ResponseProcessor>();
	private List<MessageObserver> messageObservers = new ArrayList<MessageObserver>();
	private List<Preprocessor> preprocessors = new ArrayList<Preprocessor>();
	private List<Postprocessor> postprocessors = new ArrayList<Postprocessor>();
	private List<Postprocessor> finallyProcessors = new ArrayList<Postprocessor>();
	private Resolver<ResponseProcessor> responseResolver;
	private ExceptionMapping exceptionMap = new ExceptionMapping();
	private List<Plugin> plugins = new ArrayList<Plugin>();
	private RouteDeclaration routeDeclarations = new RouteDeclaration();

	/**
	 * Create a new RestExpress service. By default, RestExpress uses port 8081.
	 * Supports JSON, and XML, providing JSEND-style wrapped responses. And
	 * displays some messages on System.out. These can be altered with the
	 * setPort(), noJson(), noXml(), noSystemOut(), and useRawResponses() DSL
	 * modifiers, respectively, as needed.
	 * 
	 * <p/>
	 * The default input and output format for messages is JSON. To change that,
	 * use the setDefaultFormat(String) DSL modifier, passing the format to use
	 * by default. Make sure there's a corresponding SerializationProcessor for
	 * that particular format. The Format class has the basics.
	 * 
	 * <p/>
	 * This DSL was created as a thin veneer on Netty functionality. The bind()
	 * method simply builds a Netty pipeline and uses this builder class to
	 * create it. Underneath the covers, RestExpress uses Google GSON for JSON
	 * handling and XStream for XML processing. However, both of those can be
	 * swapped out using the putSerializationProcessor(String,
	 * SerializationProcessor) method, creating your own instance of
	 * SerializationProcessor as necessary.
	 * 
	 * @param routes
	 *            a RouteDeclaration that declares the URL routes that this
	 *            service supports.
	 */
	public RestExpress()
	{
		super();
		setName(DEFAULT_NAME);
		supportJson(true);
		supportXml();
		useSystemOut();
	}

	public String getBaseUrl()
	{
		return routeDefaults.getBaseUrl();
	}

	public RestExpress setBaseUrl(String baseUrl)
	{
		routeDefaults.setBaseUrl(baseUrl);
		return this;
	}

	/**
	 * Get the name of this RestExpress service.
	 * 
	 * @return a String representing the name of this service suite.
	 */
	public String getName()
	{
		return serverSettings.getName();
	}

	/**
	 * Set the name of this RestExpress service suite.
	 * 
	 * @param name
	 *            the name.
	 * @return the RestExpress instance to facilitate DSL-style method chaining.
	 */
	public RestExpress setName(String name)
	{
		serverSettings.setName(name);
		return this;
	}
	
	public int getPort()
	{
		return serverSettings.getPort();
	}

	public RestExpress setPort(int port)
	{
		serverSettings.setPort(port);
		return this;
	}

	public RestExpress putResponseProcessor(String format, ResponseProcessor processor)
	{
		responseProcessors.put(format, processor);
		return this;
	}

	/* package protected */Map<String, ResponseProcessor> getResponseProcessors()
	{
		return responseProcessors;
	}

	public Resolver<ResponseProcessor> getResponseResolver()
	{
		return responseResolver;
	}

	public RestExpress setResponseResolver(Resolver<ResponseProcessor> responseResolver)
	{
		this.responseResolver = responseResolver;
		return this;
	}

	public String getDefaultFormat()
	{
		return routeDefaults.getDefaultFormat();
	}

	public RestExpress setDefaultFormat(String format)
	{
		if (format == null || format.trim().isEmpty()) return this;

		routeDefaults.setDefaultFormat(format.trim().toLowerCase());
		return this;
	}

	/**
	 * Tell RestExpress to support JSON in routes, incoming and outgoing. By
	 * default RestExpress supports JSON and is the default.
	 * 
	 * @param isDefault
	 *            true to make JSON the default format.
	 * @return the RestExpress instance.
	 */
	public RestExpress supportJson(boolean isDefault)
	{
		if (!getResponseProcessors().containsKey(Format.JSON))
		{
			responseProcessors.put(Format.JSON, ResponseProcessor.defaultJsonProcessor());
		}

		if (isDefault)
		{
			setDefaultFormat(Format.JSON);
		}

		return this;
	}

	/**
	 * Tell RestExpress to support JSON in routes, incoming and outgoing. By
	 * default RestExpress supports JSON and is the default.
	 * 
	 * @return the RestExpress instance.
	 */
	public RestExpress supportJson()
	{
		return supportJson(false);
	}

	/**
	 * Tell RestExpress to not support JSON in routes, incoming or outgoing.
	 * Client must call setDefaultFormat(String) to set the default format to
	 * something else.
	 * 
	 * @return the RestExpress instance.
	 */
	public RestExpress noJson()
	{
		responseProcessors.remove(Format.JSON);
		return this;
	}

	/**
	 * Tell RestExpress to support XML in routes, incoming and outgoing. By
	 * default RestExpress supports XML.
	 * 
	 * @param isDefault
	 *            true to make XML the default format.
	 * @return the RestExpress instance.
	 */
	public RestExpress supportXml(boolean isDefault)
	{
		if (!getResponseProcessors().containsKey(Format.XML))
		{
			getResponseProcessors().put(Format.XML, ResponseProcessor.defaultXmlProcessor());
		}

		if (isDefault)
		{
			setDefaultFormat(Format.XML);
		}

		return this;
	}

	/**
	 * Tell RestExpress to support XML in routes, incoming and outgoing. By
	 * default RestExpress supports XML.
	 * 
	 * @param isDefault
	 *            true to make XML the default format.
	 * @return the RestExpress instance.
	 */
	public RestExpress supportXml()
	{
		return supportXml(false);
	}

	/**
	 * Tell RestExpress to not support XML in routes, incoming or outgoing.
	 * 
	 * @return the RestExpress instance.
	 */
	public RestExpress noXml()
	{
		responseProcessors.remove(Format.XML);
		return this;
	}

	/**
	 * Tell RestExpress to support TXT format specifiers in routes, outgoing
	 * only at present.
	 * 
	 * @param isDefault true to make TXT the default format.
	 * @return the RestExpress instance.
	 */
	public RestExpress supportTxt(boolean isDefault)
	{
		if (!getResponseProcessors().containsKey(Format.TXT))
		{
			getResponseProcessors().put(Format.TXT, ResponseProcessor.defaultTxtProcessor());
		}

		if (isDefault)
		{
			setDefaultFormat(Format.TXT);
		}

		return this;
	}

	/**
	 * Tell RestExpress to support TXT format specifier in routes, outgoing only
	 * at present.
	 * 
	 * @return the RestExpress instance.
	 */
	public RestExpress supportTxt()
	{
		return supportTxt(false);
	}

	public RestExpress addMessageObserver(MessageObserver observer)
	{
		if (!messageObservers.contains(observer))
		{
			messageObservers.add(observer);
		}

		return this;
	}

	public List<MessageObserver> getMessageObservers()
	{
		return Collections.unmodifiableList(messageObservers);
	}

	/**
	 * Add a Preprocessor instance that gets called before an incoming message
	 * gets processed. Preprocessors get called in the order in which they are
	 * added. To break out of the chain, simply throw an exception.
	 * 
	 * @param processor
	 * @return
	 */
	public RestExpress addPreprocessor(Preprocessor processor)
	{
		if (!preprocessors.contains(processor))
		{
			preprocessors.add(processor);
		}

		return this;
	}

	public List<Preprocessor> getPreprocessors()
	{
		return Collections.unmodifiableList(preprocessors);
	}

	/**
	 * Add a Postprocessor instance that gets called after an incoming message is
	 * processed. A Postprocessor is useful for augmenting or transforming the
	 * results of a controller or adding headers, etc. Postprocessors get called
	 * in the order in which they are added.
	 * Note however, they do NOT get called in the case of an exception or error
	 * within the route.
	 * 
	 * @param processor
	 * @return
	 */
	public RestExpress addPostprocessor(Postprocessor processor)
	{
		if (!postprocessors.contains(processor))
		{
			postprocessors.add(processor);
		}

		return this;
	}

	public List<Postprocessor> getPostprocessors()
	{
		return Collections.unmodifiableList(postprocessors);
	}

	/**
	 * Add a Postprocessor instance that gets called in a finally block after
	 * the message is processed.  Finally processors are Postprocessor instances
	 * that are guaranteed to run even if an error is thrown from the controller
	 * or somewhere else in the route.  A Finally Processor is useful for adding
	 * headers or transforming results even during error conditions. Finally
	 * processors get called in the order in which they are added.
	 * 
	 * If an exception is thrown during finally processor execution, the finally processors
	 * following it are executed after printing a stack trace to the System.err stream.
	 * 
	 * @param processor
	 * @return RestExpress for method chaining.
	 */
	public RestExpress addFinallyProcessor(Postprocessor processor)
	{
		if (!postprocessors.contains(processor))
		{
			postprocessors.add(processor);
		}

		return this;
	}

	public List<Postprocessor> getFinallyProcessors()
	{
		return Collections.unmodifiableList(finallyProcessors);
	}

	public boolean shouldUseSystemOut()
	{
		return useSystemOut;
	}

	public RestExpress setUseSystemOut(boolean useSystemOut)
	{
		this.useSystemOut = useSystemOut;
		return this;
	}

	public RestExpress useSystemOut()
	{
		setUseSystemOut(true);
		return this;
	}

	public RestExpress noSystemOut()
	{
		setUseSystemOut(false);
		return this;
	}

	public boolean useTcpNoDelay()
	{
		return socketSettings.useTcpNoDelay();
	}

	public RestExpress setUseTcpNoDelay(boolean useTcpNoDelay)
	{
		socketSettings.setUseTcpNoDelay(useTcpNoDelay);
		return this;
	}

	public boolean useKeepAlive()
	{
		return serverSettings.isKeepAlive();
	}

	public RestExpress setKeepAlive(boolean useKeepAlive)
	{
		serverSettings.setKeepAlive(useKeepAlive);
		return this;
	}

	public boolean shouldReuseAddress()
	{
		return serverSettings.isReuseAddress();
	}

	public RestExpress setReuseAddress(boolean reuseAddress)
	{
		serverSettings.setReuseAddress(reuseAddress);
		return this;
	}

	public int getSoLinger()
	{
		return socketSettings.getSoLinger();
	}

	public RestExpress setSoLinger(int soLinger)
	{
		socketSettings.setSoLinger(soLinger);
		return this;
	}

	public int getReceiveBufferSize()
	{
		return socketSettings.getReceiveBufferSize();
	}

	public RestExpress setReceiveBufferSize(int receiveBufferSize)
	{
		socketSettings.setReceiveBufferSize(receiveBufferSize);
		return this;
	}

	public int getConnectTimeoutMillis()
	{
		return socketSettings.getConnectTimeoutMillis();
	}

	public RestExpress setConnectTimeoutMillis(int connectTimeoutMillis)
	{
		socketSettings.setConnectTimeoutMillis(connectTimeoutMillis);
		return this;
	}

	/**
	 * 
	 * @param elementName
	 * @param theClass
	 * @return
	 */
	public RestExpress alias(String elementName, Class<?> theClass)
	{
		routeDefaults.addXmlAlias(elementName, theClass);
		return this;
	}

	public <T extends Exception, U extends ServiceException> RestExpress mapException(
	    Class<T> from, Class<U> to)
	{
		exceptionMap.map(from, to);
		return this;
	}

	public RestExpress setExceptionMap(ExceptionMapping mapping)
	{
		this.exceptionMap = mapping;
		return this;
	}

	/**
	 * Return the number of requested NIO/HTTP-handling worker threads.
	 * 
	 * @return the number of requested worker threads.
	 */
	public int getIoThreadCount()
	{
		return serverSettings.getIoThreadCount();
	}

	/**
	 * Set the number of NIO/HTTP-handling worker threads.  This
	 * value controls the number of simultaneous connections the
	 * application can handle.
	 * 
	 * The default (if this value is not set, or set to zero) is
	 * the Netty default, which is 2 times the number of processors
	 * (or cores).
	 * 
	 * @param value the number of desired NIO worker threads.
	 * @return the RestExpress instance.
	 */
	public RestExpress setIoThreadCount(int value)
	{
		serverSettings.setIoThreadCount(value);
		return this;
	}
	
	/**
	 * Returns the number of background request-handling (executor) threads.
	 * 
	 * @return the number of executor threads.
	 */
	public int getExecutorThreadCount()
	{
		return serverSettings.getExecutorThreadPoolSize();
	}
	
	/**
	 * Set the number of background request-handling (executor) threads.
	 * This value controls the number of simultaneous blocking requests that
	 * the server can handle.  For longer-running requests, a higher number
	 * may be indicated.
	 * 
	 * For VERY short-running requests, a value of zero will cause no
	 * background threads to be created, causing all processing to occur in
	 * the NIO (front-end) worker thread.
	 * 
	 * @param value the number of executor threads to create.
	 * @return the RestExpress instance.
	 */
	public RestExpress setExecutorThreadCount(int value)
	{
		serverSettings.setExecutorThreadPoolSize(value);
		return this;
	}

	/**
	 * Set the maximum length of the content in a request. If the length of the content exceeds this value,
	 * the server closes the connection immediately without sending a response.
	 * 
	 * @param size the maximum size in bytes.
	 * @return the RestExpress instance.
	 */
	public RestExpress setMaxContentSize(int size)
	{
		serverSettings.setMaxContentSize(size);
		return this;
	}

	/**
	 * Can be called after routes are defined to augment or get data from
	 * all the currently-defined routes.
	 * 
	 * @param callback a Callback implementor.
	 */
	public void iterateRouteBuilders(Callback<RouteBuilder> callback)
	{
		routeDeclarations.iterateRouteBuilders(callback);
	}

	public Channel bind()
	{
		return bind((getPort() > 0 ? getPort() : DEFAULT_PORT));
	}

	/**
	 * The last call in the building of a RestExpress server, bind() causes
	 * Netty to bind to the listening address and process incoming messages.
	 * 
	 * @return Channel
	 */
	public Channel bind(int port)
	{
		setPort(port);

		// Configure the server.
		if (getIoThreadCount() == 0)
		{
			bootstrap = Bootstraps.createServerNioBootstrap();
		}
		else
		{
			bootstrap = Bootstraps.createServerNioBootstrap(getIoThreadCount());
		}

		// Set up the event pipeline factory.
		DefaultRequestHandler requestHandler = new DefaultRequestHandler(
		    createRouteResolver(), createResponseProcessorResolver());

		// Add MessageObservers to the request handler here, if desired...
		requestHandler.addMessageObserver(messageObservers.toArray(new MessageObserver[0]));

		requestHandler.setExceptionMap(exceptionMap);

		// Add pre/post processors to the request handler here...
		addPreprocessors(requestHandler);
		addPostprocessors(requestHandler);
		addFinallyProcessors(requestHandler);

		PipelineBuilder pf = new PipelineBuilder()
		    .addRequestHandler(requestHandler)
		    .setMaxContentLength(serverSettings.getMaxContentSize());

		if (getExecutorThreadCount() > 0)
		{
			ExecutionHandler executionHandler = new ExecutionHandler(
	             new OrderedMemoryAwareThreadPoolExecutor(getExecutorThreadCount(), 0, 0));
			pf.setExecutionHandler(executionHandler);
		}

		bootstrap.setPipelineFactory(pf);
		setBootstrapOptions();

		// Bind and start to accept incoming connections.
		if (shouldUseSystemOut())
		{
			System.out.println("Starting " + getName() + " Server on port " + port);
		}

		Channel channel = bootstrap.bind(new InetSocketAddress(port));
		allChannels.add(channel);
		bindPlugins();
		return channel;
	}

	private void setBootstrapOptions()
	{
		bootstrap.setOption("child.tcpNoDelay", useTcpNoDelay());
		bootstrap.setOption("child.keepAlive", serverSettings.isKeepAlive());
		bootstrap.setOption("reuseAddress", shouldReuseAddress());
		bootstrap.setOption("child.soLinger", getSoLinger());
		bootstrap.setOption("connectTimeoutMillis", getConnectTimeoutMillis());
		bootstrap.setOption("receiveBufferSize", getReceiveBufferSize());
	}

	/**
	 * Used in main() to install a default JVM shutdown hook and shut down the
	 * server cleanly. Calls shutdown() when JVM termination detected. To
	 * utilize your own shutdown hook(s), install your own shutdown hook(s) and
	 * call shutdown() instead of awaitShutdown().
	 */
	public void awaitShutdown()
	{
		Runtime.getRuntime().addShutdownHook(new DefaultShutdownHook(this));
		boolean interrupted = false;

		do
		{
			try
			{
				Thread.sleep(300);
			}
			catch (InterruptedException e)
			{
				interrupted = true;
			}
		}
		while (!interrupted);
	}

	/**
	 * Releases all resources associated with this server so the JVM can
	 * shutdown cleanly. Call this method to finish using the server. To utilize
	 * the default shutdown hook in main() provided by RestExpress, call
	 * awaitShutdown() instead.
	 */
	public void shutdown()
	{
		ChannelGroupFuture future = allChannels.close();
		future.awaitUninterruptibly();
		shutdownPlugins();
		bootstrap.getFactory().releaseExternalResources();
	}

	/**
	 * @return
	 */
	private RouteResolver createRouteResolver()
	{
		return new RouteResolver(routeDeclarations.createRouteMapping(routeDefaults));
	}

	/**
	 * @return
	 */
	public ServerMetadata getRouteMetadata()
	{
		ServerMetadata m = new ServerMetadata();
		m.setName(getName());
		m.setPort(getPort());
		m.setDefaultFormat(getDefaultFormat());
		m.addAllSupportedFormats(getResponseProcessors().keySet());
		m.addAllRoutes(routeDeclarations.getMetadata());
		return m;
	}

	public RestExpress registerPlugin(Plugin plugin)
	{
		if (!plugins.contains(plugin))
		{
			plugins.add(plugin);
			plugin.register(this);
		}

		return this;
	}

	private void bindPlugins()
	{
		for (Plugin plugin : plugins)
		{
			plugin.bind(this);
		}
	}

	private void shutdownPlugins()
	{
		for (Plugin plugin : plugins)
		{
			plugin.shutdown(this);
		}
	}

	/**
	 * @return
	 */
	private ResponseProcessorResolver createResponseProcessorResolver()
	{
		ResponseProcessorResolver resolver = new ResponseProcessorResolver();
		resolver.setDefaultFormat(getDefaultFormat());

		for (Entry<String, ResponseProcessor> entry : getResponseProcessors().entrySet())
		{
			if (entry.getKey().equals(Format.XML))
			{
				setXmlAliases((AliasingSerializationProcessor) entry.getValue().getSerializer());
			}

			resolver.put(entry.getKey(), entry.getValue());
		}

		return resolver;
	}

	/**
	 * @param processor
	 */
	private void setXmlAliases(AliasingSerializationProcessor processor)
	{
		routeDefaults.setXmlAliases(processor);
	}

	/**
	 * @param requestHandler
	 */
	private void addPreprocessors(DefaultRequestHandler requestHandler)
	{
		for (Preprocessor processor : getPreprocessors())
		{
			requestHandler.addPreprocessor(processor);
		}
	}

	/**
	 * @param requestHandler
	 */
	private void addPostprocessors(DefaultRequestHandler requestHandler)
	{
		for (Postprocessor processor : getPostprocessors())
		{
			requestHandler.addPostprocessor(processor);
		}
	}

	/**
	 * @param requestHandler
	 */
	private void addFinallyProcessors(DefaultRequestHandler requestHandler)
	{
		for (Postprocessor processor : getFinallyProcessors())
		{
			requestHandler.addFinallyProcessor(processor);
		}
	}


	// SECTION: ROUTE CREATION

	public ParameterizedRouteBuilder uri(String uriPattern, Object controller)
	{
		return routeDeclarations.uri(uriPattern, controller, routeDefaults);
	}

	public RegexRouteBuilder regex(String uriPattern, Object controller)
	{
		return routeDeclarations.regex(uriPattern, controller, routeDefaults);
	}
}
