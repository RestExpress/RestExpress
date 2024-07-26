RestExpress is a thin wrapper on the JBOSS Netty HTTP stack to provide a simple and easy way to
create RESTful services in Java that support massive Internet Scale and performance.

Born to be simple, only three things are required to wire up a service:

1. The main class which utilizes the RestExpress DSL to create a server instance.
2. Use a DSL for the declaration of supported URLs and HTTP methods of the service(s) (much like routes.rb in a Rails app).
3. Service controller implementation(s), which is/are a simple POJO--no interface or super class implementation.

See: https://github.com/RestExpress/RestExpress-Archetype to get started (there is a README there).

Maven Usage
===========
Stable:
```xml
		<dependency>
			<groupId>com.strategicgains</groupId>
			<artifactId>RestExpress</artifactId>
			<version>0.11.3</version>
		</dependency>
```
Development:
```xml
		<dependency>
			<groupId>com.strategicgains</groupId>
			<artifactId>RestExpress</artifactId>
			<version>0.12.0-SNAPSHOT</version>
		</dependency>
```
Or download the jar directly from: http://search.maven.org/#search%7Cga%7C1%7Ca%3A%22RestExpress%22

Note that to use the SNAPSHOT version, you must enable snapshots and a repository in your pom file as follows
(if you already have a profile with repositories in your pom, you can just copy the <repository> section):
```xml
  <profiles>
    <profile>
       <id>allow-snapshots</id>
          <activation><activeByDefault>true</activeByDefault></activation>
       <repositories>
         <repository>
           <id>sonatype-snapshots-repo</id>
           <url>https://oss.sonatype.org/content/repositories/snapshots</url>
           <releases><enabled>false</enabled></releases>
           <snapshots><enabled>true</enabled></snapshots>
         </repository>
       </repositories>
     </profile>
  </profiles>
```

A quick tutorial:
=================

Please see the Maven Archetypes at https://github.com/RestExpress/RestExpress-Archetype for kick-starting your API.

* HTTP Methods, if not changed in the fluent (DSL) interface, map to the following:
	* GET --> read(Request, Response)
	* PUT --> update(Request, Response)
	* POST --> create(Request, Response)
	* DELETE --> delete(Request, Response)

* You can choose to return objects from the methods, if desired, which will be returned to the client in the body of the response.  The object will be marshaled into JSON or XML, depending on the default or based on the format in the request (e.g. '.xml' or '?format=xml').

* If you choose to not return a value from the method (void methods) and using raw responses, then call response.setResponseNoContent() before returning to set the response HTTP status code to 204 (no content).

* On successful creation, call response.setResponseCreated() to set the returning HTTP status code to 201.

* For more real-world examples, see the https://github.com/RestExpress/RestExpress-Examples repo which contains additional projects that setup RestExpress services.  Simply do '**mvn exec:java**' to run them.  Then to see what's available perform a GET on the route: '/routes/metadata' to get a list of all the routes (or endpoints) available (e.g. localhost:8081/routes/metadata in the browser).

Change History/Release Notes:
=============================

Release 0.12.0-SNAPSHOT (in master)
-----------------------------------
* **BREAKING CHANGE** Added Request.getScheme() which makes a guess re. http/https from host. Altered Request.getUrl() to use it instead of getProtocol().
* **BREAKING CHANGE** Removed XML support. No more XStream!
* **BREAKING CHANGE** Issue#140 - Use io.netty.handler.ssl.SslContext instead of java.net.ssl.SSLContext. Using SslUtil still works. Otherwise, use SslContextBuilder.
* **BREAKING CHANGE** Issue#135 - Query-string parameter keys and values are URL decoded before putting them into the header and query-string maps.
* **BREAKING CHANGE** Changed getBaseUrl() to consider X-Forwarded-Host, Forwarded and HOST headers to enable constructing baseURL behind load balancers and proxies.
* Issue #126 - Introduced RestExpress.noCompression() to turn off response GZip and deflate encoding support (the Netty HttpContentCompressor is not put in the pipeline) for speed optimization (e.g. for small payloads).
* Issue #130 - Throw on BindException (where the server IP address/port are already in use).
* Issues #137-138 - Use char[] vs String in SslUtil (Merge request from albahrani).
* Issue #131 - RestExpress.setReadTimeoutSeconds(int seconds) or setReadTimeout(long, TimeUnit) now causes RestExpress to inject a ReadTimeoutHandler into the pipeline.
* Issues #145, 22 - Support for large, multi-part file uploads via POST. Off by default. Call restExpressInstance.supportFileUploads(true) to enable.
* Upgraded Jackson Databind to 2.12.1
* Upgraded Netty to 4.1.112.Final
* Upgraded Xstream to 1.4.11.1
* Upgraded OWASP encoder to 1.2.3
* Updated to compile with JDK 11 (use Base64 instead of DatatypeConverter in HttpBasicAuthenticationPreprocessor).
* Added Environment.loadFromEnvVars(Properties) which loads System.getenv() to override the properties file settings. Values are available in fillValues(Properties).
* Added JSON serialization and deserialization support for LocalDate as properties.
* Added Request.getXForwardedHost() and getForwarded() as well as a Forwarded class to enable parsing the parameters in the Forwarded header.

Release 0.11.3 - 10 Feb 2016
----------------------------
* Changed signature of RestExpress.serializationProvider(SerializationProvider) to return the RestExpress server instance to facilitate method chaining.
* Issue #38 - Produce error on no routes defined for server. Now throws NoRoutesDefinedException on bind() if no routes are defined.
* Issue #122 - Listen on prescribed local IP address.
* Issue #123 - Routes Exception when using PATCH method.
* Issue #125 - Changed DefaultExceptionMapper to map cause for RuntimeException (not sub-classes) so all exceptions get mapped correctly from the mapping settings. Left a version, LegacyExceptionMapper, for old behavior. If you want legacy behavior, simply call server.setExceptionMapping(new LegacyExceptionMapper());
* Upgraded to jackson-databind 2.6.0 (from 2.4.2).
* Minor fixes due to FindBugs report. There were 11 easily fixable issues reported by FindBugs: primarily equals(), hashCode() and compareTo() implementations.
* Added UnprocessableEntityException (for 422 status code).
* Upgraded to: Netty 4.0.34.Final, XStream 1.4.8, OWasp Encoder 1.2, Jackson Databind 2.7.1-1, HTTP Client (test) 4.5.1

Release 0.11.2 - 26 Jul 2015
----------------------------
* Reduced Java source and target to 1.7

Release 0.11.1 - 24 Jul 2015
----------------------------
* Fixed issue #110 - too many files open error.
* Fixed issue #108 - request.getRemoteAddress() always returns null.
* Fixed issue #94 - Load configuration properties from classpath (if available) and override with those loaded from file system.
* Fixed issue #106 - Deprecated RestExpress.setSerializationProvider() and RestExpress.getSerializationProvider() in favor of RestExpress.setDefaultSerializationProvider() and RestExpress.getDefaultSerializationProvider(). Also added new instance methods
serializationProvider() to set and get the instance's serialization provider.
* Fixed issue #89 - Made RouteBuilder.determineActionMethod() protected so subclasses can override.
* Enhanced to use EPoll if it's available on the underlying OS.
* Added support for ':in:' operator on query-string in filter operations (e.g. ?filter=name:in:a,b,c).
* Introduced RestExpress.shutdown(boolean) to enable tests to optionally wait for a complete shutdown.
* Fix for returning 416 when offset is requested for an empty resultset (from Chamal Nanayakkara).
* Updated Java destination to 1.8

Release 0.11.0 - 12 Mar 2015
----------------------------
* **Bug Fix** Strip the response body from HEAD requests to conform to [RFC 2616](http://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.4)(HTTP spec). (from Codey Whitt)
* Upgraded Netty version from 3.9.5.Final to 4.0.25.Final (from Thomas Colwell and Mathew Leigh).
* Enhanced QueryRange.asContentRange() and Response.setCollectionResponse() to support output of '*' as max items in range header for count < 0.
* Added Unit Tests to test RestExpress' ability to compress responses and decompress requests.
* Added Environment.load(String[], Class) to eliminate the need for Main.loadEnvironments() in all archetype projects.
* KNOWN ISSUE - Controllers cannot return ReferenceCounted objects that also exist in the Request object.  This will cause the transaction to fail with an IllegalReferenceCountException.  If a ReferenceCounted object needs to be returned, a separate copy of the object will need to be made (some classes, such as ByteBuf, have a .copy() method to facilitate this).

Release 0.10.6-SNAPSHOT - in '0.10.6' branch
--------------------------------------------
* **Bug Fix** Strip the response body from HEAD requests to conform to [RFC 2616](http://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.4)(HTTP spec). (from Codey Whitt)

Release 0.10.5 - 2 Dec 2014
---------------------------
* Changed FilterComponent.setValue(String) signature to setValue(Object).
* Added suite of operators (:<:, :<=:, :>:, :>=:, :=:, :!=:, :*: [as starts-with]) to filter operations.
* Added UnsupportedMediaTypeException which returns 415 HTTP status code.
* Added ContentType.SIREN
* Added RoutePlugin.flags() and .parameters() to retrieve flags and parameters, respectively.
* Altered httpRequest.xxxHeader() calls to use httpRequest.headers().xxx() to eliminate deprecation warnings.
* Upgraded to Netty 3.9.5 final.

Release 0.10.4 - 5 Sep 2014
---------------------------
* Upgraded Jackson Databind to version 2.4.1 to fix an issue with incorrect serialization for objects that have embedded objects.
* Added XSS prevention outbound encoding to GsonJsonProcessor adding GsonEncodingStringSerializer.
* Added parseFrom(Request, String[]) method to QueryFilters and QueryOrders.
* Added tests to verify output media type.
* Added tests for wrapping of exceptions in processing and preprocessors.
* Fixed bug in AbstractSerializationProvider.resolveRequest() to use best matched Media Type, if available (vs. just the Content-Type header).
* Introduced _ignore_http_status query-string parameter to force call to return 200 status (even on failure).
* Introduced Request.getMediaType() and Request.getSerializationSettings(), as well as, Response.getMediaType().
* Added new constructor for JacksonJsonProcessor and GsonJsonProcessor to be able to turn off default outbound HTML-encoding behavior.
* Refactored DefaultRequestHandler.messageReceived() method into two to allow for better instrumentation via java agents like AppDynamics (from Codey Whitt).
* Upgraded Jackson Databind to 2.4.2
* Upgraded XStream to 1.4.7
* Upgraded Netty to 3.9.4.Final
* Upgraded GSON dependency to 2.3

Release 0.10.3 - 27 May 2014
----------------------------
* Change URL Pattern matcher to allow URLs with '?' at the end, but no query-string parameters following it.
* Changed compiler output version to 1.7 (from 1.6).
* Added SSL support (from Clark Hobbie).
* Fixed error message in QueryRange.setLimit(int) from 'limit must be >= 0' to 'limit must be > 0' (from GCL).
* Changed Request.getQueryStringMap() to never return null (from Kevin Williams).
* Expose the creation of the DefaultRequestHandler (via RestExpress.buildRequestHandler()) to rest engines not using "main()" or "bind()" (from Ryan Dietrich).
* Fixed issue with QueryOrders.enforceAllowedProperties() threw erroneous exceptions.
* Added JacksonEncodingStringSerializer (including it in JacksonJsonProcessor) to outbound HTML Entity encode for possible XSS attacks.
* Updated to Jackson-Databind 2.3.3 (from 2.1.4).

Release 0.10.2 - 3 Apr 2014
---------------------------
* Refactored ExceptionMapping into an interface, extracting previous implementation into DefaultExceptionMapper.
* Added new convenience methods on Request: getBodyAsStream(), getBodyAsBytes(), getBodyAsByteBuffer().
* Added new method Request.getNamedPath() that returns only the route path pattern instead of the
  entire URL, which Request.getNamedUrl() does.
* Request.getProtocol() now returns the protocol from the underlying HttpRequest instance.
* Fixed issue in QueryOrders where only a single valid order parameters is supported. Caused IndexOutOfBoundsException.
* Fixed an issue in RestExpress.java where finally processors were assigned as post processors. This could affect some finally processor implementations, such as timers, etc. since they are now run later in the process.
* Fixed an issue with finally processors, they are now called before the message is returned to the client (after serialization), or in the finally block (if an error occurs, response is in indeterminite state).
* Introduced another factory method in ErrorResult, allowing elimination of exceptionType in output.
* Introduced RequestContext, a Map of name/value pairs much like the Log4j mapped diagnostic contexts (MDC), as an instrument for passing augmentation data from different sources to lower levels in the framework.
* Can now add supported MediaRange(s) dynamically at startup, such as application/hal+json.
* ** Breaking Change ** Repackaged org.serialization.xml to org.restexpress.serialization.xml
* Added ContentType.HAL_JSON and ContentType.HAL_XML plus new RestExpressServerTest tests.
* Introduced RoutePlugin to simplify plugins that create internal routes.

Release 0.10.1 - 24 Jan 2014
---------------------------------------------------------------------------------------------------
* Fixed NPE issue when RestExpress.setSerializationProvider() is not called.
* Fixed misspelling in JsonSerializationProcessor.java for SUPPORTED_MEDIA_TYPES.
* Enhanced QueryFilters and QueryOrders to support enforcement of appropriate filter/order
  properties—enabling the verification of appropriate orders and filters. Throws
  BadRequestException on failure.
* Removed core StringUtils in favor of common StringUtils.
* Fixed issue where default serialization processor was not used if setSerializationProcessor() was not called.
* Changed RestExpress server startup message from “Starting <name> Server on port <port>” to “<name> server listening on port <port>”

Release 0.10.0 - 3 Jan 2014
---------------------------------------------------------------------------------------------------
* ** Breaking Change ** Repackaged to 'org.restexpress...' from 'com.strategicgains.restexpress...'
* ** Breaking Change ** Re-added GSON capability from version 0.8.2, making things a little
  more pluggable with RestExpress.setSerializationProvider(SerializationProvider).
  DefaultSerializationProvider is the default. GsonSerializationProvider is also available,
  but requires adding GSON to your pom file.  Must refactor your own custom ResponseProcessor
  class into a SerializationProvider implementor.
* ** Breaking Change ** Removed RestExpress.putResponseProcessor(), .supportJson(), .supportXml(),
  .supportTxt(), .noJson(), .noXml(), noTxt() as this is all implemented in the SerializationProvider.
* Implemented content-type negotiation using Content-Type header for serialization (e.g.
  Request.getBodyAs(type)) and Accept header for deserialization. Implementation still
  favors .{format}, but uses content-type negotiation if format not supplied.
* Added RestExpress.enforceHttpSpec() and .setEnforceHttpSpec(boolean) to enable setting
  the HTTP specification enforcement.  Previously, enforcement was always turned on. Now
  default is OFF. With it off, RestExpress allows you to create non-standard (per the HTTP
  specification) responses.
* Removed com.strategicgains.restexpress.common.util.Callback interface since it wasn't being used.
* Upgraded Netty to 3.9.0 Final

Release 0.9.4.2 - 16 Oct 2013
----------------------------------------------------------------------------------------------------
* Added ErrorResultWrapper and ErrorResult to facilitate only wrapping error responses vs.
  not wrapping or JSEND-style always-wrapped responses.

Release 0.9.4 - 17 Jul 2013
---------------------------------------------------------------------------------------------------
* Fixed issue for plugins that are dependent on RouteMetadata. Fixed issue with routes
  that depend on GET, PUT, POST, DELETE as the default--wasn't generating metadata
  correctly for that corner case.
* Fixed issue with RouteBuilder metadata generation where it wouldn't include the defaults if none set on route.
* Updated javadoc for getFullPattern() and getPattern().
* Changed Route.getBaseUrl() to perform null check to avoid getting 'null' string in value.
* Combined RestExpress-Common as a sub-module and moved core RestExpress functionality
  to the 'core' sub-module.

Release 0.9.3 - 14 Jun 2013
---------------------------------------------------------------------------------------------------
* Fixed issue with setter getting called in deserialization instead of Jackson deserializer.
* Removed LogLevel enumeration due to lack of use.
* Fixed issue #61 - Large Chunked Request Causes Errors.  Added HttpChunkAggregator to the
  pipeline.
* Added RestExpress.setMaxContentSize(int) to allow limiting of total content length of requests
  even if chunked.  Default max content size is 25K.
* Added RestExpress.iterateRouteBuilders(Callback<RouteBuilder> callback) to facilitate
  plugins, etc. augmenting or extracting information from the declared routes.

Release 0.9.2 - 27 Mar 2013
---------------------------------------------------------------------------------------------------
* **DEPRECATED:** Request.getUrlDecodedHeader() and Request.getRawHeader() in favor of getHeader(). Since
  all HTTP headers and query-string parameters are URL decoded before being put on the Request
  object, these methods are redundant and cause problems.  Their functionality was also changed
  to simply call getHeader()--so no URL decoding is done in getUrlDecodedHeader().
* Ensured that parameters extracted from the URL are decoded before setting them as headers
  on the Request.  Now all headers are URL decoded before any call to Request.getHeader(String).
* Added Request.getRemoteAddress(), which returns the remote address of the request originator.
* Merged pull request (Issue #58) from amitkarmakar13: add List&lt;String&gt; getHeaders(String)
* Removed '?' and '#' as valid path segment characters in UrlPattern to conform better with
  IETF RFC 3986, section 3.3-path. Made '{format}' a first-class element for matching URL route
  patterns (by using '{format}' instead of a regex to match).

Release 0.9.1 - 4 Mar 2013
---------------------------------------------------------------------------------------------------
* **BREAKING CHANGE:** eliminated GSON. RestExpress now uses Jackson for JSON processing.
  The changes are localized to the 'serialization' package.  Simply copy the ObjectIdDeserializer,
  ObjectIdSerializer and JsonSerializationProcessor from https://github.com/RestExpress/RestExpress-Scaffold/tree/master/mongodb/src/main/java/com/strategicgains/restexpress/scaffold/mongodb/serialization
  for MongoDB-based projects.  Or just the JsonSerializationProcessor from https://github.com/RestExpress/RestExpress-Scaffold/tree/master/minimal/src/main/java/com/strategicgains/restexpress/scaffold/minimal/serialization
  for a minimal project.
* **BREAKING CHANGE:** Removed Chunking and compression settings. RestExpress does not support
  chunking/streaming uploads.  So the setting were superfluous.  The facility is still there
  to support streaming downloads, however, and these will be chunked as necessary. As compression
  is based on the Accept header, support is always provided--settings are superfluous.
  NOTE: streaming downloads are not fully implemented yet.
* **BREAKING CHANGE:** Removed LoggingHandler from the Netty pipeline and related setter methods.
* Added HttpBasicAuthenticationPreprocessor to facilitate HTTP Basic Authentication. Added
  Flags.Auth.PUBLIC_ROUTE, NO_AUTHENTICATION, and NO_AUTHORIZATION to support configuration
  of HttpBasicAuthenticationPreprocessor (and other authentication/authorization
  related routes).

Release 0.8.2 - 19 Feb 2013
---------------------------------------------------------------------------------------------------
* Fixed issue in Request.parseQueryString() to URL decode query-string parameters before putting
  them in the Request header.

Release 0.8.1 - 16 Jan 2013
---------------------------------------------------------------------------------------------------
* Removed Ant-build artifacts.
* Extracted Query-related classes into RestExpress-Common.
* Fixed maven compile plugin to generate Java target and source for 1.6
* Updated Netty dependency to 3.6.2.Final
* Removed dependency on HyperExpress.

Release 0.8.0 - 09 Jan 2013
---------------------------------------------------------------------------------------------------
* Pushed to Maven Central repository.
* Introduced maven build.
* Merged pull request #49 - Added method to get all headers from a HttpRequest.
* Fixed issue #40 (https://github.com/RestExpress/RestExpress/issues/40).
* Introduced route 'aliases' where there are multiple URLs for a given service.
* Introduced concept of "finally" processors, which are executed in the finally block of
  DefaultRequestHandler and all of them are executed even if an exception is thrown within one
  of them.  This enable the CorsHeaderPlugin to set the appropriate header even on not found
  errors, etc.
* Changed to support multiple response types with wrapping or not, etc. Now can support wrapped
  JSON (.wjson) and XML (.wxml) as well as un-wrapped JSON (.json) and XML (.xml) depending on the
  format specifier.
* Now throws BadRequestException (400) if the specified format (e.f. .json) isn't supported by the
  service suite.
* Now throws MethodNotAllowedException (405) if the requested URL matches a route but not for the
  requested HTTP method.  Sets the HTTP Allow header to a comma-delimited list of accepted methods.
* Removed StringUtils.parseQueryString() as it was previously deprecated--use QueryStringParser.
* Introduced String.join() methods (2).
* Removed JSONP handling, favoring use of CORS instead, introducing CorsHeaderPlugin and corresponding post-processor.
* Wraps ETAG header in quotes.
* Renamed QueryRange.stop to QueryRange.limit.
* Removed need for RouteDefinition class, moving that functionality into the RestExpress builder.
* Changed example apps to reflect above elimination of RouteDefinition class.

===================================================================================================
Release 0.7.4 - 30 Nov 2012 (branch 'v0.7.4')
---------------------------------------------------------------------------------------------------
* Patch release to allow period ('.') as a valid character within URL parameters. Note that this
  now allows a period to be the last character on the URL whether there is a format-specifier
  parameter declared for that route, or not. Also, if the route supports the format specifier and
  there is a period in the last parameter of the URL, anything after the last period will be used
  as the format for the request--which may NOT be what you want.
  -- /foo/todd.fredrich --> /foo/{p1}.{format} will use 'fredrich' as the format
  -- /foo/todd.fredrich.json --> /foo/{p1}.{format} will use 'json' as the format
  -- /foo/todd. --> /foo/{p1}.{format} will contain 'todd.' for the value of p1
  -- /foo/todd. --> /foo/{p1} will contain 'todd.' for the value of p1

===================================================================================================
Release 0.7.3 - 12 July 2012 (branch 'v0.7.3')
---------------------------------------------------------------------------------------------------
* Patch release to fix an issue with i18n. Fixed issue with
  DefaultJsonProcessor.deserialize(ChannelBuffer, Class) where underlying InputStreamReader was
  not UTF-8.

==================================================================================================
Release 0.7.2 - 14 May 2012
---------------------------------------------------------------------------------------------------
* Introduced ExecutionHandler with configuration via RestExpress.setExecutorThreadCount(int)
  to off-load long-running requests from the NIO workers into a separate thread pool.
* Introduced CacheControlPlugin which leverages CacheHeaderPostprocessor, DateHeaderPostprocessor
  and EtagHeaderPostprocessor to respond to GET requests.
* Introduced EtagHeaderPostprocessor which adds ETag header in response to GET requests.
* Introduced DateHeaderPostprocessor which adds a Date header to responses to GET requests.
* Introduced CacheHeaderPostprocessor which support Cache-Control and other caching-related
  response header best-practices by setting Parameters.Cache.MAX_AGE or Flags.Cache.DONT_CACHE on
  a route.
* Changed to use QueryStringParser over StringUtils for query-string and QueryStringDecoder for
  body parsing. This mitigates HashDoS attacks, since the query-string is parsed before a request
  is accepted.
* Deprecated StringUtils in favor of using Netty's QueryStringDecoder or
  RestExpress's QueryStringParser.
* Refactored so SerializationProcessor.resolve(Request) is only called once at the end of the
  request cycle (performance enhancement).

===================================================================================================
Release 0.7.1 - 20 Sep 2011
---------------------------------------------------------------------------------------------------
* Added rootCause to ResultWrapper data area.
* Exposed the XStream object from DefaultXmlProcessor.
* Renamed Link to XLink.
* Renamed LinkUtils to XLinkUtils, adding asXLinks() method that utilizes XLinkFactory callback
  to create the XLink instances.
* Changed URL Matching to support additional characters: '[', ']', '&' which more closely follows
  W3C the specification.
* Added ability to return query string parameters as a Map from the Request.
* Introduced Request.getBaseUrl() which returns protocol and host, without URL path information.
* Introduced query criteria capability: filter, order, range (for pagination).
* Introduced the concept of Plugins.
* Refactored the console routes to use the new plugin concept.
* Updated Netty jars (to 3.2.5).
* Added ability to set the number of worker threads via call to RestExpress.setWorkerThreadCount()
  before calling bind().

===================================================================================================
Release 0.7.0
---------------------------------------------------------------------------------------------------
* Added gzip request/response handling. On by default. Disable it via call to
  RestExpress.noCompression() and supportCompression().
* Added chunked message handling. On by default. Chunking settings are managed via
  RestExpress.noChunkingSupport(), supportChunking(), and setMaxChunkSize(int).

===================================================================================================
Release 0.6.1.1 - 31 Mar 2011
---------------------------------------------------------------------------------------------------
* Bug fix to patch erroneously writing to already closed channel in
  DefaultRequestHandler.exceptionCaught().

===================================================================================================
Release 0.6.1 - 30 Mar 2011
---------------------------------------------------------------------------------------------------
* Stability release.
* Fixed issue when unable to URL Decode query string parameters or URL.
* Introduced SerializationResolver that defines a getDefault() method. Implemented
  SerializationResolver in DefaultSerializationResolver.
* Changed UrlPattern to match format based on non-whitespace vs. word characters.
* Refactored Request.getHeader() into getRawHeader(String) and getUrlDecodedHeader(String), along
  with corresponding getRawHeader(String,String) and getUrlDecodedHeader(String,String).
* Renamed realMethod property to effectiveHttpMethod, along with appropriate accessor/mutator.
* Removed Request from Response constructor signature.
* Added FieldNamingPolicy to DefaultJsonProcessor (using LOWER_CASE_WITH_UNDERSCORES).
* getUrlDecodedHeader(String) throws BadRequestException if URL decoding fails.

===================================================================================================
Release 0.6.0.2 - 21 Mar 2011
---------------------------------------------------------------------------------------------------
* Fixed issue with 'connection reset by peer' causing unresponsive behavior.
* Utilized Netty logging behavior to add logging capabilities to RestExpress.
* Made socket-level settings externally configurable:  tcpNoDelay, KeepAlive, reuseAddress,
  soLinger, connectTimeoutMillis, receiveBufferSize.
* Merged in 0.5.6.1 path release changes.
* Added enforcement of some HTTP 1.1 specification rules: Content-Type, Content-Length and body
  content for 1xx, 204, 304 are not allow.  Now throws HttpSpecificationException if spec. is
  not honored.
* Added ability to add 'flags' and 'parameters' to routes, in that, uri().flag("name") on Route
  makes test request.isFlagged("name") return true. Also, uri().parameter("name", "value")
  makes request.getParamater("name") return "value". Not returned/marshaled in the response.
  Useful for setting internal values/flags for preprocessors, controllers, etc.
* Added .useRawResponse() and .useWrappedResponse() to fluent route DSL.  Causes that particular
  route to wrap the response or not, independent of global response wrap settings.
* Parameters parsed from the URL and query string arguments are URL decoded before being placed
  as Request headers.

===================================================================================================
Release 0.6.0.1
---------------------------------------------------------------------------------------------------
* Issue #7 - Fixed issue with invalid URL requested where serialization always occurred to the
             default (JSON).  Now serializes to the requested format, if applicable.
* Issue #11 - Feature enhancement for Kickstart.  Now utilizes Rails-inspired configuration
              environements (e.g. dev, testing, prod, etc.).
* Issue #12 - Parse URL parameter names out of the URL pattern and include them in the route
              metadata output.

===================================================================================================
Release 0.6.0
---------------------------------------------------------------------------------------------------
* Routes now defined in descendant of RouteDeclaration.
* Refactored everything into RestExpress object, using builder pattern for configuration.
* Implemented RestExpress DSL to declare REST server in main().
* Added supported formats and default format to RouteBuilder.
* Added JSEND-style response wrapping (now default).  Call RestExpress.useWrappedResponses() to use.
* Add ability to support raw response return.  Call RestExpress.useRawResponses() to use.
* Implemented /console/routes.{format} route which return metadata about the routes in this
  service suite. To use, call RestExpress.supportConsoleRoutes().
* Exceptions occurring now return in the requested format with the message wrapped and using the
  appropriate mime type (e.g. application/json or application/xml).
* Kickstart application has complete build with 'dist' target that builds a runnable jar file and
  'run' target that will run the services from the command line.
* Kickstart application now handles JVM shutdown correctly using JVM shutdown hooks.  The method
  RestExpress.awaitShutdown() uses the DefaultShutdownHook class.  RestExpress.shutdown() allows
  programs to use their own shutdown hooks, calling RestExpress.shutdown() upon shudown to release
  all resource.

===================================================================================================
Release 0.5.6.1 - 11 Mar 2011
---------------------------------------------------------------------------------------------------
* Patch release to fix issue with HTTP response status of 204 (No Content) and 304 (Not Modified)
  where they would return a body of an empty string and content length of 2 ('\r\n').  No longer
  serializes for 204 or 304.  Also no longer serializes for null body response unless a JSONP header
  is passed in on the query string.

===================================================================================================
Release 0.5.6 - 18 Jan 2011
---------------------------------------------------------------------------------------------------
* Upgraded to Netty 3.2.3 final.
* Added getProtocol(), getHost(), getPath() to Request
* Functionality of getUrl() is now getPath() and getUrl() now returns the entire URL string,
  including protocol, host and port, and path.

===================================================================================================
Release 0.5.5
---------------------------------------------------------------------------------------------------
* Added regex URL matching with RouteMapping.regex(String) method.
* Refactored Route into an abstract class, moving previous functionality into ParameterizedRoute.
* Added KickStart release artifact to get projects going quickly--simply unzip the kickstart file.
* Added SimpleMessageObserver which performs simple timings and outputs to System.out.

===================================================================================================
Release 0.5.4
---------------------------------------------------------------------------------------------------
* Added alias() capability to DefaultTxtProcessor to facilitate custom text serialization.
* Updated kickstart application to illustrate latest features.
* Minor refactoring of constants and their locations (moved to RestExpress.java).

===================================================================================================
Release 0.5.3
---------------------------------------------------------------------------------------------------
* Fixed issue with JSON date/timestamp parsing.
* Fixed issues with XML date/timestamp parsing.
* Upgraded to GSON 1.6 release.
* Added correlation ID to Request to facilitate timing, etc. in pipeline.
* Added alias(String, Class) to DefaultXmlProcessor.
* By default, alias List and Link in DefaultXmlProcessor.

===================================================================================================
Release 0.5.2
---------------------------------------------------------------------------------------------------
* Introduced DateJsonProcessor (sibling to DefaultJsonProcessor) which parses dates vs. time points.
* Refactored ExceptionMapping.getExceptionFor() signature from Exception to Throwable.
* Introduced MessageObserver, which accepts notifications of onReceived(), onSuccess(), onException(), onComplete() to facilitate logging, auditing, timing, etc.
* Changed RouteResolver.resolve() to throw NotFoundException instead of BadRequestException for unresolvable URI.

===================================================================================================
Release 0.5.1
---------------------------------------------------------------------------------------------------
* Enhanced support for mark, unreserved and some reserved characters in URL. Specifically, added
  $-+*()~:!' and %.  Still doesn't parse URLs with '.' within the string itself--because of the
  support for .{format} URL section.

===================================================================================================
Release 0.5
---------------------------------------------------------------------------------------------------
* Renamed repository from RestX to RestExpress.
* Repackaged everything from com.strategicgains.restx... to com.strategicgains.restexpress...
* Changed DefaultHttpResponseWriter to output resonse headers correctly.
* Updated javadoc on RouteBuilder to provide some documentation on route DSL.

===================================================================================================
Release 0.4
---------------------------------------------------------------------------------------------------
* Fixed error in "Connection: keep-alive" processing during normal and error response writing.
* Can now create route mappings for OPTIONS and HEAD http methods.
* Added decoding to URL when Request is constructed.
* Improved pre-processor implementation, including access to resolved route in request.
* Better null handling here and there to avoid NullPointerException, including serialization resolver.
* Improved UT coverage.
* KickStart application builds and is a more accurate template.

===================================================================================================
Release 0.3
---------------------------------------------------------------------------------------------------
* Added support for "method tunneling" in POST via query string parameter (e.g. _method=PUT or _method=DELETE)
* Added JSONP support. Use jsonp=<method_name> in query string.
* Utilized Builder pattern in DefaultPipelineFactory, which is now PipelineBuilder.
* Externalized DefaultRequestHandler in PipelineBuilder and now supports pre/post processors (with associated interfaces).
