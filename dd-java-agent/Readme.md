# Datadog Java Agent for APM

This is a Java Agent made for instrumenting Java applications using the Datadog Tracer. Once attached to one of your JVM you should see traces into your [Datadog APM](https://app.datadoghq.com/apm/search).

Tracing instrumentations can be done in 2 ways:

- Automatically over a set of [supported Web servers, frameworks or database drivers](#instrumented-frameworks)
- By using the [`@trace` annotation](#custom-instrumentations)

:heavy_exclamation_mark: **Warning:** This library is currently at Alpha stage. This means that even if we rigorously tested instrumentations you may experience strange behaviors depending on your running environment. It must evolve quickly though. For any help please contact [support@datadoghq.com](mailto:support@datadoghq.com).

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

We assume that your `${M2_REPO}` env variable is properly setted. Don't forget to replace the `{version}` placeholder in the following commands.

- So first download the `jar` file from the main Maven repository:

```
> mvn dependency:get -Dartifact=com.datadoghq:dd-java-agent:{version}
```

- Then add the following JVM argument when launching your application (in IDE, using Maven run or simply in collaboration with the `>java -jar` command):

```
-javaagent:${M2_REPO}/com/datadoghq/dd-java-agent/0.0.1/dd-java-agent-{version}.jar
```

That's it! If you did this properly the agent was executed at pre-main, had detected and instrumented the supported libraries and custom traces. You should then see traces on [Datadog APM](https://app.datadoghq.com/apm/search).


## Instrumented frameworks

When attached to an application the `dd-java-agent` automatically  instruments the following set of frameworks & servers.

### Frameworks

| FWK        | Versions           | Comments  |
| ------------- |:-------------:| ----- |
| OkHTTP | 3.x | HTTP client calls with [cross-process](http://opentracing.io/documentation/pages/api/cross-process-tracing.html) headers |
| Apache HTTP Client | 4.x |HTTP client calls with [cross-process](http://opentracing.io/documentation/pages/api/cross-process-tracing.html) headers|
| AWS SDK | 1.x | Trace all client calls to any AWS service |
| Web Servlet Filters| Depending on server | See [Servers](#servers) section |

### Servers

| FWK        | Versions           | Comments  |
| ------------- |:-------------:| -----|
| Jetty | 8.x, 9.x  | Trace all incoming HTTP calls with [cross-process](http://opentracing.io/documentation/pages/api/cross-process-tracing.html) capabilities |
| Tomcat |   8.0.x, 8.5.x & 9.x   |  Trace all incoming HTTP calls with [cross-process](http://opentracing.io/documentation/pages/api/cross-process-tracing.html) capabilities  |

Modern web application frameworks such as Dropwizard or Spring Boot are automatically instrumented thanks to these servers instrumentation. (See [example projects](#other-useful-resources)) 

### Databases
| FWK        | Versions           | Comments  |
| ------------- |:-------------:| ----- |
|Spring JDBC| 4.x | Please check the following [JDBC instrumentation](#jdbc-instrumentation) section |
|Hibernate| 5.x | Please check the following [JDBC instrumentation](#jdbc-instrumentation) section |
| MongoDB | 3.x | Intercepts all the calls from the MongoDB client |
| ElasticSearch | 3.x, 5.x | Intercepts all the calls from the ES client |

#### JDBC instrumentation

By enabling the JDBC instrumentation you'll  intercept all the client calls to the following DBs: MySQL, PostgreSQL, H2, HSQLDB, IBM DB2, SQL Server, Oracle, MariaDB, etc...

But unfortunately this can not be done entirely automatically today. To enable tracing please follow the instructions provided on the [java-jdbc opentracing contrib project](https://github.com/opentracing-contrib/java-jdbc#usage).

We also provide an [example project with Spring Boot & MySQL](web application frameworks).

## Custom instrumentations

### The `@trace` annotation

By adding the `@trace` annotation to a method the `dd-java-agent` automatically measures the execution time.

```java
@Trace
public void myMethod() throws InterruptedException{
		...
}
```

By default, the operation name attach to the spawn span will be the name of the method and no meta tags will be attached.

You can use the the `operationName` and `tagsKV` attributes to customize your trace:

```java
@Trace(operationName="Before DB",tagsKV={"mytag","myvalue"})
public void myMethod() throws InterruptedException{
	....
}
``` 

### Enabling custom tracing

- Add the agent as a dependency of your project

```xml
<dependency>
	<groupId>com.datadoghq</groupId>
	<artifactId>dd-java-agent</artifactId>
	<version>{version}</version>
</dependency>
```

- Enable custom tracing by adding this JVM property `-Ddd.enable_custom_tracing`

If you want to see custom tracing in action please run the [Dropwizard example](https://github.com/DataDog/dd-trace-java/blob/dev/dd-trace-examples/dropwizard-mongo-client/).

## Other useful resources

Before instrumenting your own project you might want to run the provided examples:

- [Dropwizard/MongoDB & Cross process client calls](https://github.com/DataDog/dd-trace-java/blob/dev/dd-trace-examples/dropwizard-mongo-client/)
- [Springboot & MySQL over JDBC](https://github.com/DataDog/dd-trace-java/tree/dev/dd-trace-examples/spring-boot-jdbc)

Other links that you might want to read:

- Install on [Docker](https://app.datadoghq.com/apm/docs/tutorials/docker)
- Datadog's APM [Terminology](https://app.datadoghq.com/apm/docs/tutorials/terminology)
- [FAQ](https://app.datadoghq.com/apm/docs/tutorials/faq)

