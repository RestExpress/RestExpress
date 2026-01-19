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
package org.restexpress;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.restexpress.domain.metadata.RouteMetadata;
import org.restexpress.domain.metadata.ServerMetadata;
import org.restexpress.exception.DefaultExceptionMapper;
import org.restexpress.exception.ExceptionMapping;
import org.restexpress.exception.ServiceException;
import org.restexpress.pipeline.DefaultRequestHandler;
import org.restexpress.pipeline.FileUploadHandler;
import org.restexpress.pipeline.MessageObserver;
import org.restexpress.pipeline.PipelineInitializer;
import org.restexpress.pipeline.Postprocessor;
import org.restexpress.pipeline.Preprocessor;
import org.restexpress.plugin.Plugin;
import org.restexpress.response.DefaultHttpResponseWriter;
import org.restexpress.route.RouteBuilder;
import org.restexpress.route.RouteDeclaration;
import org.restexpress.route.RouteResolver;
import org.restexpress.route.parameterized.ParameterizedRouteBuilder;
import org.restexpress.route.regex.RegexRouteBuilder;
import org.restexpress.serialization.DefaultSerializationProvider;
import org.restexpress.serialization.SerializationProvider;
import org.restexpress.settings.RouteDefaults;
import org.restexpress.settings.ServerSettings;
import org.restexpress.settings.SocketSettings;
import org.restexpress.util.Callback;
import org.restexpress.util.DefaultShutdownHook;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.AdaptiveRecvByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelOption;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.ssl.SslContext;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.GlobalEventExecutor;

/**
 * Primary entry point to create a RestExpress service. All that's required is a
 * RouteDeclaration. By default: port is 8081, serialization format is JSON,
 * supported formats are JSON and XML.
 *
 * @author toddf
 */
public class RestExpress
{
//	static
//	{
//		ResourceLeakDetector.setLevel(Level.DISABLED);
//	}

	public static final String DEFAULT_HANDLER_NAME = "default";
	public static final String FILEUPLOAD_HANDLER_NAME = "fileupload";
	public static final String DEFAULT_NAME = "RestExpress";
    private static final ChannelGroup ALL_CHANNELS = new DefaultChannelGroup(DEFAULT_NAME, GlobalEventExecutor.INSTANCE);
	public static final int DEFAULT_PORT = 8081;

	private static SerializationProvider defaultSerializationProvider = null;

	private SocketSettings socketSettings = new SocketSettings();
	private ServerSettings serverSettings = new ServerSettings();
	private RouteDefaults routeDefaults = new RouteDefaults();
	private boolean enforceHttpSpec = false;
	private boolean useSystemOut;
	private ServerBootstrapFactory bootstrapFactory = new ServerBootstrapFactory();

	private List<MessageObserver> messageObservers = new ArrayList<>();
	private List<Preprocessor> preprocessors = new ArrayList<>();
	private List<Postprocessor> postprocessors = new ArrayList<>();
	private List<Postprocessor> finallyProcessors = new ArrayList<>();
	private ExceptionMapping exceptionMap = new DefaultExceptionMapper();
	private List<Plugin> plugins = new ArrayList<>();
	private RouteDeclaration routeDeclarations = new RouteDeclaration();
	private SslContext sslContext = null;
	private SerializationProvider serializationProvider = null;

	/**
	 * Change the default behavior for serialization.
	 * If no SerializationProcessor is set, default of DefaultSerializationProcessor is used,
	 * which uses Jackson for JSON, XStream for XML.
	 * 
	 * @param provider a SerializationProvider instance.
	 * @deprecated use setDefaultSerializationProvider()
	 */
	@Deprecated(forRemoval = true)
	public static void setSerializationProvider(SerializationProvider provider)
	{
		setDefaultSerializationProvider(provider);
	}

	/**
	 * @return the default serialization provider.
	 * @deprecated Use getDefaultSerializationProvider()
	 */
	@Deprecated(forRemoval = true)
	public static SerializationProvider getSerializationProvider()
	{
		return getDefaultSerializationProvider();
	}

	/**
	 * Change the default behavior for serialization.
	 * If no SerializationProvider is set, default of DefaultSerializationProvider is used,
	 * which uses Jackson for JSON, XStream for XML.
	 * 
	 * @param provider a SerializationProvider instance.
	 */
	public static void setDefaultSerializationProvider(SerializationProvider provider)
	{
		defaultSerializationProvider = provider;
	}

	/**
	 * Get the default serialization provider for RestExpress. If the value is
	 * unset DefaultSerializationProcessor is set as the default and returned.
	 * Otherwise, the previously-set value for the default is returned.
	 * 
	 * @return the default serialization provider.
	 */
	public static SerializationProvider getDefaultSerializationProvider()
	{
		if (defaultSerializationProvider == null)
		{
			defaultSerializationProvider = new DefaultSerializationProvider();
		}

		return defaultSerializationProvider;
	}

	/**
	 * Change the serialization provider for this server instance.
	 * If no SerializationProcessor is set, default of DefaultSerializationProcessor is used,
	 * which uses Jackson for JSON, XStream for XML.
	 * 
	 * @param provider a SerializationProvider instance.
	 * @return this RestExpress server instance.
	 */
	public RestExpress serializationProvider(SerializationProvider provider)
	{
		this.serializationProvider = provider;
		return this;
	}

	/**
	 * Get the serialization provider for this server instance. If none has
	 * been set, it is set to the default serialization processor and returned.
	 * Otherwise, the setting for this server is returned.
	 * 
	 * @return the SerializationProvider for this instance, or the default.
	 */
	public SerializationProvider serializationProvider()
	{
		if (serializationProvider == null)
		{
			serializationProvider(getDefaultSerializationProvider());
		}

		return serializationProvider;
	}

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
	 */
	public RestExpress()
	{
		super();
		setName(DEFAULT_NAME);
		useSystemOut();
	}

	public RestExpress setSSLContext(SslContext sslContext)
	{
		this.sslContext = sslContext;
		return this;
	}

	public SslContext getSSLContext()
	{
		return sslContext;
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

	public String getHostname()
	{
		return serverSettings.getHostname();
	}

	public boolean hasHostname()
	{
		return serverSettings.hasHostname();
	}

	/**
	 * Set the hostname or IP address that the server will listen on.
	 * 
	 * @param hostname hostname or IP address.
	 */
	public void setHostname(String hostname)
	{
		serverSettings.setHostname(hostname);
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
	 * Add a Postprocessor instance that gets called right before the serialized
	 * message is sent to the client, or in a finally block after the message is
	 * processed, if an error occurs.  Finally processors are Postprocessor instances
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
		if (!finallyProcessors.contains(processor))
		{
			finallyProcessors.add(processor);
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

	public RestExpress setEnforceHttpSpec(boolean enforceHttpSpec)
	{
		this.enforceHttpSpec = enforceHttpSpec;
		return this;
	}

	public RestExpress enforceHttpSpec()
	{
		setEnforceHttpSpec(true);
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

	/**
	 * Turns off the Netty HttpContentCompressor on binding so that output GZip and deflate encodings are not possible.
	 * This is a speed optimization, per Issue #126
	 * 
	 * By default, compression is supported. Use this to turn it off.
	 * 
	 * @return this RestExpress instance.
	 */
	public RestExpress noCompression()
	{
		serverSettings.setUseCompression(false);
		return this;
	}

	/**
	 * Answers whether the service is setup to use response compression via the Netty HttpContentCompressor.
	 * 
	 * @return true if the RestExpress server is configured to use response compression. Otherwise, false.
	 */
	public boolean isUsingCompression()
	{
		return serverSettings.shouldUseCompression();
	}

	public RestExpress setSupportFileUpload(boolean value)
	{
		serverSettings.setSupportFileUpload(value);
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

	public RestExpress setReadTimeoutSeconds(int timeoutSeconds)
	{
		serverSettings.setReadTimeoutSeconds(timeoutSeconds);
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
	throws Throwable
	{
		return bind((getPort() > 0 ? getPort() : DEFAULT_PORT));
	}

	/**
	 * Build a default request handler. Used instead of bind() so it may be used
	 * injected into any existing Netty pipeline.
	 *
	 * @return ChannelHandler
	 */
	public ChannelHandler buildRequestHandler()
	{
		// Set up the event pipeline factory.
		DefaultRequestHandler requestHandler = new DefaultRequestHandler(
		    createRouteResolver(), serializationProvider(),
		    new DefaultHttpResponseWriter(), enforceHttpSpec);

		// Add MessageObservers to the request handler here, if desired...
		requestHandler.addMessageObserver(messageObservers.toArray(new MessageObserver[0]));

		requestHandler.setExceptionMap(exceptionMap);

		// Add pre/post processors to the request handler here...
		addPreprocessors(requestHandler);
		addPostprocessors(requestHandler);
		addFinallyProcessors(requestHandler);

		return requestHandler;
	}

	public FileUploadHandler buildFileUploadRequestHandler() {
		FileUploadHandler fileUploadHandler = new FileUploadHandler(
				createRouteResolver(), serializationProvider(),
				new DefaultHttpResponseWriter(), enforceHttpSpec);

		fileUploadHandler.setExceptionMap(exceptionMap);

		return fileUploadHandler;
	}


	/**
	 * The last call in the building of a RestExpress server, bind() causes
	 * Netty to bind to the listening address and process incoming messages.
	 *
	 * @return Channel
	 */
	public Channel bind(int port)
	throws Throwable
	{
		setPort(port);

		if (hasHostname())
		{
			return bind(new InetSocketAddress(getHostname(), port));
		}

		return bind(new InetSocketAddress(port));
	}

	/**
	 * Bind to a particular hostname or IP address and port.
	 * 
	 * @param hostname
	 * @param port
	 * @return
	 */
	public Channel bind(String hostname, int port)
	throws Throwable
	{
		setPort(port);
		return bind(new InetSocketAddress(hostname, port));
	}

	public Channel bind(InetSocketAddress ipAddress)
	throws Throwable
	{
		ServerBootstrap bootstrap = bootstrapFactory.newServerBootstrap(getIoThreadCount());
		PipelineInitializer pi = new PipelineInitializer()
			.setExecutionHandler(initializeExecutorGroup())
		    .addRequestHandler(DEFAULT_HANDLER_NAME, buildRequestHandler())
		    .setSSLContext(sslContext)
		    .setMaxContentLength(serverSettings.getMaxContentSize())
		    .setReadTimeout(serverSettings.getReadTimeout(), serverSettings.getReadTimeoutUnit())
		    .setUseCompression(serverSettings.shouldUseCompression());

		if (serverSettings.isSupportFileUpload())
		{
			pi.setSupportFileUpload(true)
				.addRequestHandler(FILEUPLOAD_HANDLER_NAME, buildFileUploadRequestHandler());
		}
		
		bootstrap.childHandler(pi);

		setBootstrapOptions(bootstrap);

		// Bind and start to accept incoming connections.
		if (shouldUseSystemOut())
		{
			System.out.println(getName() + " server listening on port " + ipAddress.toString());
		}

		ChannelFuture channelFuture = bootstrap.bind(ipAddress);
		channelFuture.addListener(new GenericFutureListener<ChannelFuture>()
		{
			@Override
			public void operationComplete(ChannelFuture future)
			throws Exception
			{
				ALL_CHANNELS.add(future.channel());
			}
		});

		channelFuture.awaitUninterruptibly();

		if (!channelFuture.isSuccess())
		{
			throw channelFuture.cause();
		}

		// Connection established successfully
		bindPlugins();
		return channelFuture.channel();
	}

	private EventExecutorGroup initializeExecutorGroup()
    {
		if (getExecutorThreadCount() > 0)
		{
			return new DefaultEventExecutorGroup(getExecutorThreadCount(), Thread.ofVirtual().factory());
		}

		return null;
    }

	private void setBootstrapOptions(ServerBootstrap bootstrap)
	{
		bootstrap.option(ChannelOption.SO_BACKLOG, 1024);
		bootstrap.option(ChannelOption.SO_REUSEADDR, shouldReuseAddress());
		bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, getConnectTimeoutMillis());
		bootstrap.option(ChannelOption.SO_RCVBUF, getReceiveBufferSize());
		bootstrap.option(ChannelOption.RCVBUF_ALLOCATOR, new AdaptiveRecvByteBufAllocator());

		bootstrap.childOption(ChannelOption.SO_KEEPALIVE, useKeepAlive());
		bootstrap.childOption(ChannelOption.TCP_NODELAY, useTcpNoDelay());
		bootstrap.childOption(ChannelOption.SO_LINGER, getSoLinger());
		bootstrap.childOption(ChannelOption.ALLOCATOR, new PooledByteBufAllocator(true));
		bootstrap.option(ChannelOption.RCVBUF_ALLOCATOR, new AdaptiveRecvByteBufAllocator());
		bootstrap.childOption(ChannelOption.SO_RCVBUF, getReceiveBufferSize());
		bootstrap.childOption(ChannelOption.SO_REUSEADDR, shouldReuseAddress());
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
	 * <p/>
	 * Same as shutdown(false);
	 */
	public void shutdown()
	{
		shutdown(false);
	}

	/**
	 * Releases all resources associated with this server so the JVM can
	 * shutdown cleanly. Call this method to finish using the server. To utilize
	 * the default shutdown hook in main() provided by RestExpress, call
	 * awaitShutdown() instead.
	 * 
	 * @param shouldWait true if shutdown() should wait for the shutdown of each thread group.
	 */
	public void shutdown(boolean shouldWait)
	{
		ChannelGroupFuture channelFuture = ALL_CHANNELS.close();
		bootstrapFactory.shutdownGracefully(shouldWait);
		channelFuture.awaitUninterruptibly();
		shutdownPlugins();
	}

	/**
	 * @return
	 */
	private RouteResolver createRouteResolver()
	{
		return new RouteResolver(routeDeclarations.createRouteMapping(routeDefaults));
	}

	/**
	 * Retrieve metadata about the routes in this RestExpress server.
	 *
	 * @return ServerMetadata instance.
	 */
	public ServerMetadata getRouteMetadata()
	{
		ServerMetadata m = new ServerMetadata();
		m.setName(getName());
		m.setPort(getPort());
		m.addAllRoutes(routeDeclarations.getMetadata());
		return m;
	}

	/**
	 * Retrieve the named routes in this RestExpress server, creating a Map of
	 * them by name, with the value portion being populated with the URL
	 * pattern. Any '.{format}' portion of the URL pattern is omitted.
	 * <p/>
	 * If the Base URL is set, it is included in the URL pattern.
	 * <p/>
	 * Only named routes are included in the output.
	 *
	 * @return a Map of Route Name/URL pairs.
	 */
	public Map<String, String> getRouteUrlsByName()
	{
		final Map<String, String> urlsByName = new HashMap<>();

		iterateRouteBuilders(routeBuilder -> {
			RouteMetadata route = routeBuilder.asMetadata();

			if (route.getName() != null)
			{
				urlsByName.put(route.getName(), getBaseUrl()
				    + route.getUri().getPattern().replace(".{format}", ""));
			}
		});

		return urlsByName;
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
