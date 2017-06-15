# Datadog Java Agent for APM

*Minimal Java version required: 1.7*

This is a Java Agent to instrument Java applications using the Datadog Tracer. Once attached to one of your JVM you should see traces in [Datadog APM](https://app.datadoghq.com/apm/search).

Tracing instrumentation can be done in 2 ways:

- Automatically over a set of [supported Web servers, frameworks or database drivers](#instrumented-frameworks)
- By using the [`@Trace` annotation](#custom-instrumentations)

:heavy_exclamation_mark: **Warning:** This library is currently in Alpha. This means that even though we rigorously tested instrumentations you may experience strange behaviors depending on your running environment. Be sure to test thoroughly on a staging environment before releasing to production. For any help please contact [tracehelp@datadoghq.com](mailto:tracehelp@datadoghq.com).

## Quick start

### 1. Install the Datadog Agent on your OS

The Java instrumentation library works in collaboration with a local agent that transmits the traces to Datadog. To install it with tracing please follow these steps:

- Run the latest [Datadog Agent](https://app.datadoghq.com/account/settings#agent) (version 5.11.0 or above)
- [Enable APM in the Datadog Agent configuration file](https://app.datadoghq.com/apm/docs/tutorials/configuration) `/etc/dd-agent/datadog.conf`.

```
[Main]
# Enable the trace agent.
apm_enabled: true
```
- [Restart the Agent](http://docs.datadoghq.com/guides/basic_agent_usage/)

### 2. Instrument your application

To instrument your project or your servers you simply have to declare the provided `jar` file in your JVM arguments as a valid `-javaagent:`.

- So first download the `jar` file from the main repository.

```
# use latest version 
curl -OL http://central.maven.org/maven2/com/datadoghq/dd-java-agent/{version}/dd-java-agent-{version}.jar
```

- Then add the following JVM argument when launching your application (in IDE, using Maven run or simply in collaboration with the `>java -jar` command):

```
-javaagent:/path/to/the/dd-java-agent-{version}.jar
```

That's it! If you did this properly the agent was executed at pre-main, had detected and instrumented the supported libraries and custom traces. You should then see traces on [Datadog APM](https://app.datadoghq.com/apm/search).

## Configuration

Configuration is done through a default `dd-trace.yaml` file as a resource in the classpath.
You can also override it by adding the file path as a system property when launching the JVM: `-Ddd.trace.configurationFile`.

```yaml
# Main service name for the app
defaultServiceName: java-app

# The writer to use.
# Could be: LoggingWritter or DDAgentWriter (default)
writer:
  # LoggingWriter: Spans are logged using the application configuration
  # DDAgentWriter: Spans are forwarding to a Datadog trace Agent
  #  - Param 'host': the hostname where the DD trace Agent is running (default: localhost)
  #  - Param 'port': the port to reach the DD trace Agent (default: 8126)
  type: DDAgentWriter
  host: localhost
  port: 8126

# The sampler to use.
# Could be: AllSampler (default) or RateSampler
sampler:
  # AllSampler: all spans are reported to the writer
  # RateSample: only a portion of spans are reported to the writer
  #  - Param 'rate': the portion of spans to keep
  type: AllSampler
  # Skip some traces if the root span tag values matches some regexp patterns
  # skipTagsPatterns: {"http.url": ".*/demo/add.*"}
  
# Enable custom tracing (Custom annotations for now)
# enableCustomAnnotationTracingOver: ["io","org","com"]

# Disable some instrumentations
# disabledInstrumentations: ["apache http", "mongo", "jetty", "tomcat", ...]
```

## Instrumented frameworks

When attached to an application the `dd-java-agent` automatically instruments the following set of frameworks & servers:

### Frameworks

| FWK        | Versions           | Comments  |
| ------------- |:-------------:| ----- |
| OkHTTP | 3.x | HTTP client calls with [cross-process](http://opentracing.io/documentation/pages/api/cross-process-tracing.html) headers |
| Apache HTTP Client | 4.3 + |HTTP client calls with [cross-process](http://opentracing.io/documentation/pages/api/cross-process-tracing.html) headers|
| AWS SDK | 1.x | Trace all client calls to any AWS service |
| Web Servlet Filters| Depending on server | See [Servers](#servers) section |

### Servers

| Server        | Versions           | Comments  |
| ------------- |:-------------:| -----|
| Jetty | 8.x, 9.x  | Trace all incoming HTTP calls with [cross-process](http://opentracing.io/documentation/pages/api/cross-process-tracing.html) capabilities |
| Tomcat |   8.0.x, 8.5.x & 9.x   |  Trace all incoming HTTP calls with [cross-process](http://opentracing.io/documentation/pages/api/cross-process-tracing.html) capabilities  |

Modern web application frameworks such as Dropwizard or Spring Boot are automatically instrumented thanks to these servers instrumentation. (See [example projects](#other-useful-resources))

### Databases
| DB        | Versions           | Comments  |
| ------------- |:-------------:| ----- |
| Spring JDBC| 4.x | Please check the following [JDBC instrumentation](#jdbc-instrumentation) section |
| Hibernate| 5.x | Please check the following [JDBC instrumentation](#jdbc-instrumentation) section |
| MongoDB | 3.x | Intercepts all the calls from the MongoDB client |
| Cassandra | 3.2.x | Intercepts all the calls from the Cassandra client |

#### JDBC instrumentation

By enabling the JDBC instrumentation you'll  intercept all the client calls to the following DBs: MySQL, PostgreSQL, H2, HSQLDB, IBM DB2, SQL Server, Oracle, MariaDB, etc...

But unfortunately this can not be done entirely automatically today. To enable tracing please follow the instructions provided on the [java-jdbc opentracing contrib project](https://github.com/opentracing-contrib/java-jdbc#usage).

We also provide an [example project with Spring Boot & MySQL](web application frameworks).

### Disabling instrumentations

If for some reason you need to disable an instrumentation you should uncomment the `disabledInstrumentations: ` attribute in the configuration and provide a list as illustrated below:

```yaml
...

# Disable a few instrumentations
disabledInstrumentations: ["apache http", "mongo", "tomcat"]

...
```

### 

## Custom instrumentations

### The `@Trace` annotation

By adding the `@Trace` annotation to a method the `dd-java-agent` automatically measures the execution time.

```java
@Trace
public void myMethod() throws InterruptedException{
		...
}
```

By default, the operation name attached to the spawn span will be the name of the method and no meta tags will be attached.

You can use the `operationName` and `tagsKV` attributes to customize your trace:

```java
@Trace(operationName="Before DB",tagsKV={"mytag","myvalue"})
public void myMethod() throws InterruptedException{
	....
}
``` 

### Enabling custom tracing

- Add the annotations jar as a dependency of your project

```xml
<dependency>
	<groupId>com.datadoghq</groupId>
	<artifactId>dd-trace-annotations</artifactId>
	<version>{version}</version>
</dependency>
```

- Enable custom tracing by adding in the `dd-trace.yaml` config file the packages you would like to scan as follow `enableCustomAnnotationTracingOver: ["io","org","com"]`.

If you want to see custom tracing in action please run the [Dropwizard example](https://github.com/DataDog/dd-trace-java/blob/dev/dd-trace-examples/dropwizard-mongo-client/).

## Other useful resources

Before instrumenting your own project you might want to run the provided examples:

- [Dropwizard/MongoDB & Cross process client calls](https://github.com/DataDog/dd-trace-java/blob/dev/dd-trace-examples/dropwizard-mongo-client/)
- [Springboot & MySQL over JDBC](https://github.com/DataDog/dd-trace-java/tree/dev/dd-trace-examples/spring-boot-jdbc)

Other links that you might want to read:

- Install on [Docker](https://app.datadoghq.com/apm/docs/tutorials/docker)
- Datadog's APM [Terminology](https://app.datadoghq.com/apm/docs/tutorials/terminology)
- [FAQ](https://app.datadoghq.com/apm/docs/tutorials/faq)

