## What is Datadog APM?

Datadog APM traces the path of each request through your application stack, recording the latency of each step along the way. It sends all tracing data to Datadog, where you can easily identify which services or calls are slowing down your application the most.
 
This repository contains what you need to trace Java applications. Two quick notes up front:

- **Datadog Java APM is currently in Beta**
- Datadog Java APM can only trace applications running Java 1.7 or later

## The Components

These three things help you instrument Java applications:

**[Datadog Java Agent](https://github.com/DataDog/dd-trace-java/tree/master/dd-java-agent)**: a Java Agent that, when passed to your application:
1. Automatically traces many Java frameworks, application servers, and databases using some of the libraries from [opentracing-contrib](https://github.com/opentracing-contrib), and
2. Lets you add annotations to your methods to easily trace their execution times.

**[Datadog Tracer](https://github.com/DataDog/dd-trace-java/tree/master/dd-trace)**: an OpenTracing-compatible library that lets you trace any piece of your Java code, not just whole methods.

**[Datadog APM Agent](https://github.com/DataDog/datadog-trace-agent)**: a (non-Java) service that runs on your application servers, accepting trace data from the Datadog Java Agent and/or Datadog Tracer and sending it to Datadog. (The APM Agent is not part of this repo; it's the same Agent to which all Datadog tracers—Go, Python, etc—send data)

## Getting Started

Before instrumenting your code, [install the Datadog Agent](https://app.datadoghq.com/account/settings#agent) on your application servers (or locally, if you're just trying out Java APM) and enable the APM Agent. See the special instructions for [macOS](https://github.com/DataDog/datadog-trace-agent#run-on-osx) and [Docker](https://github.com/DataDog/docker-dd-agent#tracing--apm) if you're using either.

### Automatic Tracing

#### Java Agent Setup

Download the latest Datadog Java Agent:

```
$ wget -O dd-java-agent.jar 'https://oss.jfrog.org/artifactory/oss-snapshot-local/com/datadoghq/dd-java-agent/0.1.2-SNAPSHOT/dd-java-agent-0.1.2-20170725.120841-20.jar'
```

Then create a file `dd-trace.yaml` anywhere in your application's classpath (or provide the file's path via `-Ddd.trace.configurationFile` when starting the application):

```yaml
# Main service name for the app
defaultServiceName: my-java-app

writer:
  type: DDAgentWriter # send traces to Datadog Trace Agent; only other option is LoggingWriter
  host: localhost     # host/IP address where Datadog Trace Agent listens
  port: 8126          # port where Datadog Trace Agent listens

sampler:
  type: AllSampler # Collect 100% of traces; only other option is RateSample
# rate: 0.5        # if using type: RateSample, uncomment to collect only 50% of traces

# Skip traces whose root span tag values matches some these regexps; useful if you want to skip health checks traces from your service stats
# skipTagsPatterns: {"http.url": ".*/demo/add.*"}
```

**Note:** this configuration file is also required for [Manual Instrumentation](#manual-instrumentation) with the Datadog Tracer.

Finally, add the following JVM argument when starting your application—in your IDE, your Maven or gradle application script, or your `java -jar` command:

```
-javaagent:/path/to/the/dd-java-agent.jar
```

The Java Agent—once passed to your application—automatically traces requests to the frameworks, application servers, and databases shown below. It does this by using various libraries from [opentracing-contrib](https://github.com/opentracing-contrib). In most cases you don't need to install or configure anything; traces will automatically show up in your Datadog dashboards. The exception is [any database library that uses JDBC](#jdbc).

#### Frameworks

| Framework        | Versions           | Comments  |
| ------------- |:-------------:| ----- |
| [OkHTTP](https://github.com/opentracing-contrib/java-okhttp) | 3.x | HTTP client calls with [cross-process](http://opentracing.io/documentation/pages/api/cross-process-tracing.html) headers |
| [Apache HTTP Client](https://github.com/opentracing-contrib/java-apache-httpclient) | 4.3 + | HTTP client calls with [cross-process](http://opentracing.io/documentation/pages/api/cross-process-tracing.html) headers|
| [AWS SDK](https://github.com/opentracing-contrib/java-aws-sdk) | 1.11.119+ | Trace all client calls to any AWS service |
| [Web Servlet Filters](https://github.com/opentracing-contrib/java-web-servlet-filter) | Depends on web server | See [Application Servers](#application-servers) |
| [JMS 2](https://github.com/opentracing-contrib/java-jms) | 2.x | Trace calls to message brokers; distributed trace propagation not yet supported |

#### Application Servers

| Server | Versions | Comments |
| ------------- |:-------------:| -----|
| Jetty | 8.x, 9.x | HTTP client calls with [cross-process](http://opentracing.io/documentation/pages/api/cross-process-tracing.html) headers |
| Tomcat | 8.0.x, 8.5.x & 9.x | HTTP client calls with [cross-process](http://opentracing.io/documentation/pages/api/cross-process-tracing.html) headers |

Requests to any web frameworks that use these application servers—Dropwizard and Spring Boot, for example—are automatically traced as well.

#### Databases

| Database      | Versions           | Comments  |
| ------------- |:-------------:| ----- |
| Spring JDBC| 4.x | **NOT traced automatically**—see [JDBC instructions](#jdbc) |
| Hibernate | 5.x | **NOT traced automatically**—see [JDBC instructions](#jdbc) |
| [MongoDB](https://github.com/opentracing-contrib/java-mongo-driver) | 3.x | Intercepts all the calls from the MongoDB client |
| [Cassandra](https://github.com/opentracing-contrib/java-cassandra-driver) | 3.2.x | Intercepts all the calls from the Cassandra client |

To disable tracing for any of these libraries, list them in `disabledInstrumentations` within `dd-trace.yaml`:

```yaml
...

# Disable tracing on these
disabledInstrumentations: ["opentracing-apache-httpclient", "opentracing-mongo-driver", "opentracing-web-servlet-filter"]
```

See [this YAML file](src/main/resources/dd-trace-supported-framework.yaml) for the proper names of all supported libraries (i.e. the names as you must list them in `disabledInstrumentations`).

#### JDBC

The Java Agent doesn't automatically trace requests to databases whose drivers are JDBC-based. For such databases, you must:

1. Add the opentracing-jdbc dependency to your project, e.g. for Maven, add this to pom.xml:

```
<dependency>
    <groupId>io.opentracing.contrib</groupId>
    <artifactId>opentracing-jdbc</artifactId>
    <version>0.0.3</version>
</dependency>
```

2. Modify your code's database connection strings, e.g. for a connection string `jdbc:h2:mem:test`, make it `jdbc:tracing:h2:mem:test`.

### The `@Trace` Annotation

The Java Agent lets you add a `@Trace` annotation to any method to measure its execution time. Setup the [Java Agent](#java-agent-setup) first if you haven't done so.

#### Setup

Add the `dd-trace-annotations` dependency to your project. For Maven, add this to pom.xml:

```xml
<dependency>
	<groupId>com.datadoghq</groupId>
	<artifactId>dd-trace-annotations</artifactId>
	<version>{version}</version>
</dependency>
```

For gradle, add:

```gradle
compile group: 'com.datadoghq', name: 'dd-trace-annotations', version: {version}
```

Then, in `dd-trace.yaml`, list any applications where you want to use `@Trace`:

```yaml
enableCustomAnnotationTracingOver: ["com.example.myproject"]`.
```

The Java Agent lets you use `@Trace` not just for `com.example.myproject`, but also for any application whose name _begins_ like that, e.g. `com.example.myproject.foobar`. If you're tempted to list something like `["com", "io"]` to avoid having to fuss with this configuration as you add new projects, be careful; providing `@Trace`-ability to too many applications could hurt your package's build time.

#### Example

Add an annotation to some method in your code:

```java
@Trace
public void myMethod() throws InterruptedException{
		...
}
```

You can pass an `operationName` to name the trace data as you want:

```java
@Trace(operationName="database.before")
public void myMethod() throws InterruptedException{
	....
}
```

When you don't pass an `operationName`, the Java Agent sets it to the method name.

### Manual Instrumentation

You can use the Datadog Tracer (`dd-trace`) library to measure execution times for specific pieces of code. This lets you trace your application more precisely than you can with the Java Agent alone.

#### Setup

For Maven, add this to pom.xml:

```xml
        <!-- OpenTracing API -->
        <dependency>
            <groupId>io.opentracing</groupId>
            <artifactId>io.opentracing:opentracing-api</artifactId>
            <version>0.30.0</version>
        </dependency>

        <!-- OpenTracing Util -->
        <dependency>
            <groupId>io.opentracing</groupId>
            <artifactId>io.opentracing:opentracing-util</artifactId>
            <version>0.30.0</version>
        </dependency>
        
        <!-- Datadog Tracer (only needed if you do not use dd-java-agent) -->
        <dependency>
            <groupId>com.datadoghq</groupId>
            <artifactId>dd-trace</artifactId>
            <version>${dd-trace-java.version}</version>
        </dependency>
```

For gradle, add:

```
        compile group: 'io.opentracing', name: 'opentracing-api', version: "0.30.0"
        compile group: 'io.opentracing', name: 'opentracing-util', version: "0.30.0"
        compile group: 'com.datadoghq', name: 'dd-trace', version: "${dd-trace-java.version}"
```

#### Examples

Rather than referencing classes directly from `dd-trace` (other than registering `DDTracer`), we strongly suggest using the [OpenTracing API](https://github.com/opentracing/opentracing-java).
[Additional documentation on the api](docs/opentracing-api.md) is also available. 

Let's look at a simple example.

```java
class InstrumentedClass {
    
    void method0() {
        // 1. Make sure dd-trace.yaml is in your resources directory
        // 2. If using the Java Agent (-javaagent;/path/to/agent.jar), do not instantiate the GlobalTracer; the Agent instantiates it for you
        Tracer tracer = io.opentracing.util.GlobalTracer.get();
        
        Span span = tracer.buildSpan("operation-name").startActive();
        span.setTag(DDTags.SERVICE_NAME, "my-new-service"); 
        
        // The code you're tracing
        Thread.sleep(1000);
        
        // If you don't call finish(), the span data will NOT make it to Datadog!
        span.finish();   
    }
}
``` 

Alternatively, you can wrap the code you want to trace in a `try-with-resources` statement:

```java
class InstrumentedClass {

    void method0() {
        Tracer tracer = io.opentracing.util.GlobalTracer.get();

        try (ActiveSpan span = tracer.buildSpan("operation-name").startActive()) {
            span.setTag(DDTags.SERVICE_NAME, "my-new-service");
            Thread.sleep(1000);
        }
    }
}
```

In this case, you don't need to call `span.finish()`.

Finally, you must provide a configured tracer. This can be easily done by using the `TracerFactory` or manually
in the bootstrap method (i.e. `main`).

```java
public class Application {

    public static void main(String[] args) {
	
        // Initialize the tracer from the configuration file      
        Tracer tracer = DDTracerFactory.createFromConfigurationFile();
        io.opentracing.util.GlobalTracer.register(tracer);
        
        // OR from the API
        Writer writer = new com.datadoghq.trace.writer.DDAgentWriter();
        Sampler sampler = new com.datadoghq.trace.sampling.AllSampler();
        Tracer tracer = new com.datadoghq.trace.DDTracer(writer, sampler);
        io.opentracing.util.GlobalTracer.register(tracer);
        
        // ...
    }
}
```

`DDTracerFactory` looks for `dd-trace.yaml` in the classpath. 

## Further Reading

- Browse the [example applications](dd-trace-examples) in this repository to see Java tracing in action
- Read [OpenTracing's documentation](https://github.com/opentracing/opentracing-java); feel free to use the Trace Java API to customize your instrumentation.
- Brush up on [Datadog APM Terminology](https://docs.datadoghq.com/tracing/terminology/)
- Read the [Datadog APM FAQ](https://docs.datadoghq.com/tracing/faq/)

## Get in touch
 
If you have questions or feedback, email us at tracehelp@datadoghq.com or chat with us in the datadoghq slack channel #apm-java.
