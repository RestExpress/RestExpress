# RestExpress: Microservices Framework for Java

RestExpress is a thin wrapper on the JBOSS Netty HTTP stack to provide a simple and easy way to
create RESTful services in Java that support massive Internet Scale and performance.

Born to be simple, only three things are required to wire up a service:

1. The main class which utilizes the RestExpress DSL to create a server instance.
2. Use a DSL for the declaration of supported URLs and HTTP methods of the service(s) (much like routes.rb in a Rails app).
3. Service controller implementation(s), which is/are a simple POJO--no interface or super class implementation.

See: https://github.com/RestExpress/RestExpress-Archetype to get started (there is a README there).

# Maven Usage

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

# A quick tutorial:

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

# Project Roadmap

See [ROADMAP.md](ROADMAP.md) for the modernization, OpenAPI, MCP, and production-readiness roadmap.

# Release Notes & Change History

See [RELEASE_NOTES.md](RELEASE_NOTES.md) for the historical changes and release notes.
