---

<p align="center">
  <strong>
    <a href="https://github.com/open-telemetry/opentelemetry-java-instrumentation#getting-started">Getting Started</a>
    &nbsp;&nbsp;&bull;&nbsp;&nbsp;
    <a href="https://github.com/open-telemetry/community#special-interest-groups">Getting Involved</a>
    &nbsp;&nbsp;&bull;&nbsp;&nbsp;
    <a href="https://github.com/open-telemetry/opentelemetry-java-instrumentation/discussions">Getting In Touch</a>
  </strong>
</p>

<p align="center">
  <a href="https://github.com/open-telemetry/opentelemetry-java-instrumentation/actions?query=workflow%3A%22Nightly+build%22">
    <img alt="Build Status" src="https://img.shields.io/github/workflow/status/open-telemetry/opentelemetry-java-instrumentation/Nightly%20build?style=for-the-badge">
  </a>
  <a href="https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases">
    <img alt="GitHub release (latest by date including pre-releases)" src="https://img.shields.io/github/v/release/open-telemetry/opentelemetry-java-instrumentation?include_prereleases&style=for-the-badge">
  </a>
  <a href="https://bintray.com/open-telemetry/maven/opentelemetry-java-instrumentation">
    <img alt="Bintray" src="https://img.shields.io/bintray/v/open-telemetry/maven/opentelemetry-java-instrumentation?style=for-the-badge">
  </a>
  <img alt="Beta" src="https://img.shields.io/badge/status-beta-informational?style=for-the-badge&logo=data:image/png;base64,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">
</p>

<p align="center">
  <strong>
    <a href="CONTRIBUTING.md">Contributing<a/>
    &nbsp;&nbsp;&bull;&nbsp;&nbsp;
    <a href="docs/scope.md">Scope<a/>
    &nbsp;&nbsp;&bull;&nbsp;&nbsp;
    <a href="docs/ga-requirements.md">Roadmap<a/>
  </strong>
</p>

---

# <img src="https://opentelemetry.io/img/logos/opentelemetry-logo-nav.png" alt="OpenTelemetry Icon" width="45" height=""> OpenTelemetry Instrumentation for Java

This project provides a Java agent JAR that can be attached to any Java 8+
application and dynamically injects bytecode to capture telemetry from a
number of popular libraries and frameworks.
You can export the telemetry data in a variety of formats.
You can also configure the agent and exporter via command line arguments
or environment variables. The net result is the ability to gather telemetry
data from a Java application without code changes.

## Getting Started

Download the [latest version](https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent-all.jar).

This package includes the instrumentation agent as well as
instrumentations for all supported libraries and all available data exporters.
The package provides a completely automatic, out-of-the-box experience.

Enable the instrumentation agent using the `-javaagent` flag to the JVM.
```
java -javaagent:path/to/opentelemetry-javaagent-all.jar \
     -jar myapp.jar
```
By default, the OpenTelemetry Java agent uses
[OTLP exporter](https://github.com/open-telemetry/opentelemetry-java/tree/master/exporters/otlp)
configured to send data to
[OpenTelemetry collector](https://github.com/open-telemetry/opentelemetry-collector/blob/master/receiver/otlpreceiver/README.md)
at `localhost:55680`.

Configuration parameters are passed as Java system properties (`-D` flags) or
as environment variables. See below for a full list of environment variables. For example:
```
java -javaagent:path/to/opentelemetry-javaagent-all.jar \
     -Dotel.exporter=zipkin \
     -jar myapp.jar
```

Specify the external exporter JAR file using the `otel.exporter.jar` system property:
```
java -javaagent:path/to/opentelemetry-javaagent-all.jar \
     -Dotel.exporter.jar=path/to/external-exporter.jar \
     -jar myapp.jar
```

Learn how to add custom instrumentation in the [Manually Instrumenting](#manually-instrumenting)
section.

## Configuration parameters (subject to change!)

Note: These parameter names are very likely to change over time, so please check
back here when trying out a new version! Please report any bugs or unexpected
behavior you find.

#### Exporters

The following configuration properties are common to all exporters:

| System property | Environment variable | Purpose                                                                                                                                                 |
|-----------------|----------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------|
| otel.exporter   | OTEL_EXPORTER        | The exporter to be used. Use a comma-separated list for multiple exporters. Currently does not support multiple metric exporters. Default is `otlp`. |

##### OTLP exporter (both span and metric exporters)

A simple wrapper for the OpenTelemetry Protocol (OTLP) span and metric exporters of opentelemetry-java.

| System property              | Environment variable        | Description                                                               |
|------------------------------|-----------------------------|---------------------------------------------------------------------------|
| otel.exporter=otlp (default) | OTEL_EXPORTER=otlp          | Select the OpenTelemetry exporter (default)                                   |
| otel.exporter.otlp.endpoint  | OTEL_EXPORTER_OTLP_ENDPOINT | The OTLP endpoint to connect to. Default is `localhost:55680`.            |
| otel.exporter.otlp.insecure  | OTEL_EXPORTER_OTLP_INSECURE | Whether to enable client transport security for the connection            |
| otel.exporter.otlp.headers   | OTEL_EXPORTER_OTLP_HEADERS  | Key-value pairs separated by semicolons to pass as request headers        |
| otel.exporter.otlp.timeout   | OTEL_EXPORTER_OTLP_TIMEOUT  | The maximum waiting time allowed to send each batch. Default is `1000`.   |

To configure the service name for the OTLP exporter, add the `service.name` key
to the OpenTelemetry Resource ([see below](#opentelemetry-resource)), e.g. `OTEL_RESOURCE_ATTRIBUTES=service.name=myservice`.

##### Jaeger exporter

A simple wrapper for the Jaeger exporter of opentelemetry-java. This exporter uses gRPC for its communications protocol.

| System property                   | Environment variable              | Description                                                                                        |
|-----------------------------------|-----------------------------------|----------------------------------------------------------------------------------------------------|
| otel.exporter=jaeger              | OTEL_EXPORTER=jaeger              | Select the Jaeger exporter                                                                         |
| otel.exporter.jaeger.endpoint     | OTEL_EXPORTER_JAEGER_ENDPOINT     | The Jaeger gRPC endpoint to connect to. Default is `localhost:14250`.                              |
| otel.exporter.jaeger.service.name | OTEL_EXPORTER_JAEGER_SERVICE_NAME | The service name of this JVM instance. Default is `unknown`.                                       |

##### Zipkin exporter
A simple wrapper for the Zipkin exporter of opentelemetry-java. It sends JSON in [Zipkin format](https://zipkin.io/zipkin-api/#/default/post_spans) to a specified HTTP URL.

| System property                   | Environment variable              | Description                                                                                                               |
|-----------------------------------|-----------------------------------|-----------------------------------------------------------------------------------------------------------------------|
| otel.exporter=zipkin              | OTEL_EXPORTER=zipkin              | Select the Zipkin exporter                                                                                             |
| otel.exporter.zipkin.endpoint     | OTEL_EXPORTER_ZIPKIN_ENDPOINT     | The Zipkin endpoint to connect to. Default is `http://localhost:9411/api/v2/spans`. Currently only HTTP is supported. |
| otel.exporter.zipkin.service.name | OTEL_EXPORTER_ZIPKIN_SERVICE_NAME | The service name of this JVM instance. Default is `unknown`.                                                          |

##### Prometheus exporter
A simple wrapper for the Prometheus exporter of opentelemetry-java.

| System property               | Environment variable          | Description                                                                        |
|-------------------------------|-------------------------------|------------------------------------------------------------------------------------|
| otel.exporter=prometheus      | OTEL_EXPORTER=prometheus      | Select the Prometheus exporter                                                     |
| otel.exporter.prometheus.port | OTEL_EXPORTER_PROMETHEUS_PORT | The local port used to bind the prometheus metric server. Default is `9464`.       |
| otel.exporter.prometheus.host | OTEL_EXPORTER_PROMETHEUS_HOST | The local address used to bind the prometheus metric server. Default is `0.0.0.0`. |

##### Logging exporter

The logging exporter prints the name of the span along with its
attributes to stdout. It's mainly used for testing and debugging.

| System property              | Environment variable         | Description                                                                  |
|------------------------------|------------------------------|------------------------------------------------------------------------------|
| otel.exporter=logging        | OTEL_EXPORTER=logging        | Select the logging exporter                                                  |
| otel.exporter.logging.prefix | OTEL_EXPORTER_LOGGING_PREFIX | An optional string printed in front of the span name and attributes.         |

#### Propagator

The propagators determine which distributed tracing header formats are used, and which baggage propagation header formats are used.


| System property  | Environment variable | Description                                                                                                     |
|------------------|----------------------|-----------------------------------------------------------------------------------------------------------------|
| otel.propagators | OTEL_PROPAGATORS     | The propagators to be used. Use a comma-separated list for multiple propagators. Supported propagators are `tracecontext`, `baggage`, `b3`, `b3multi`, `jaeger`, `ottracer`, and `xray`. Default is `tracecontext,baggage` (W3C). |

#### OpenTelemetry Resource

The [OpenTelemetry Resource](https://github.com/open-telemetry/opentelemetry-specification/blob/master/specification/resource/sdk.md)
is a representation of the entity producing telemetry.

| System property          | Environment variable     | Description                                                                        |
|--------------------------|--------------------------|------------------------------------------------------------------------------------|
| otel.resource.attributes | OTEL_RESOURCE_ATTRIBUTES | Specify resource attributes in the following format: key1=val1,key2=val2,key3=val3 |

#### Peer service name

The [peer service name](https://github.com/open-telemetry/opentelemetry-specification/blob/master/specification/trace/semantic_conventions/span-general.md#general-remote-service-attributes) is the name of a remote service being connected to. It corresponds to `service.name` in the [Resource](https://github.com/open-telemetry/opentelemetry-specification/tree/master/specification/resource/semantic_conventions#service) for the local service.

| System property                     | Environment variable              | Description                                                                      |
|------------------------------------|------------------------------------|----------------------------------------------------------------------------------|
| otel.endpoint.peer.service.mapping | OTEL_ENDPOINT_PEER_SERVICE_MAPPING | Used to specify a mapping from hostnames or IP addresses to peer services, as a comma-separated list of host=name pairs. The peer service is added as an attribute to a span whose host or IP match the mapping. For example, if set to 1.2.3.4=cats-service,dogs-abcdef123.serverlessapis.com=dogs-api, requests to `1.2.3.4` will have a `peer.service` attribute of `cats-service` and requests to `dogs-abcdef123.serverlessapis.com` will have an attribute of `dogs-api`. |

#### Batch span processor

| System property           | Environment variable      | Description                                                                        |
|---------------------------|---------------------------|------------------------------------------------------------------------------------|
| otel.bsp.schedule.delay   | OTEL_BSP_SCHEDULE_DELAY   | The interval, in milliseconds, between two consecutive exports. Default is `5000`. |
| otel.bsp.max.queue        | OTEL_BSP_MAX_QUEUE        | The maximum queue size. Default is `2048`.                                             |
| otel.bsp.max.export.batch | OTEL_BSP_MAX_EXPORT_BATCH | The maximum batch size. Default is `512`.                                              |
| otel.bsp.export.timeout   | OTEL_BSP_EXPORT_TIMEOUT   | The maximum allowed time, in milliseconds, to export data. Default is `30000`.         |
| otel.bsp.export.sampled   | OTEL_BSP_EXPORT_SAMPLED   | Whether only sampled spans should be exported. Default is `true`.                  |

#### Trace config

| System property                 | Environment variable            | Description                                                  |
|---------------------------------|---------------------------------|--------------------------------------------------------------|
| otel.config.sampler.probability | OTEL_CONFIG_SAMPLER_PROBABILITY | Sampling probability between 0 and 1. Default is `1`.        |
| otel.config.max.attrs           | OTEL_CONFIG_MAX_ATTRS           | The maximum number of attributes per span. Default is `32`.  |
| otel.config.max.events          | OTEL_CONFIG_MAX_EVENTS          | The maximum number of events per span. Default is `128`.     |
| otel.config.max.links           | OTEL_CONFIG_MAX_LINKS           | The maximum number of links per span. Default is `32`        |
| otel.config.max.event.attrs     | OTEL_CONFIG_MAX_EVENT_ATTRS     | The maximum number of attributes per event. Default is `32`. |
| otel.config.max.link.attrs      | OTEL_CONFIG_MAX_LINK_ATTRS      | The maximum number of attributes per link. Default is `32`.  |

#### Interval metric reader

| System property          | Environment variable     | Description                                                                       |
|--------------------------|--------------------------|-----------------------------------------------------------------------------------|
| otel.imr.export.interval | OTEL_IMR_EXPORT_INTERVAL | The interval, in milliseconds, between pushes to the exporter. Default is `60000`.|

##### Customizing the OpenTelemetry SDK

*Customizing the SDK is highly advanced behavior and is still in the prototyping phase. It may change drastically or be removed completely. Use
with caution*

The OpenTelemetry API exposes SPI [hooks](https://github.com/open-telemetry/opentelemetry-java/blob/master/api/src/main/java/io/opentelemetry/spi/trace/TracerProviderFactory.java)
for customizing its behavior, such as the `Resource` attached to spans or the `Sampler`.

Because the automatic instrumentation runs in a different classpath than the instrumented application, it is not possible for customization in the application to take advantage of this customization. In order to provide such customization, you can provide the path to a JAR file, including an SPI implementation using the system property `otel.initializer.jar`. Note that this JAR needs to shade the OpenTelemetry API in the same way as the agent does. The simplest way to do this is to use the same shading configuration as the agent from [here](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/cfade733b899a2f02cfec7033c6a1efd7c54fd8b/java-agent/java-agent.gradle#L39). In addition, you must specify the `io.opentelemetry.javaagent.shaded.io.opentelemetry.api.trace.spi.TraceProvider` to the name of the class that implements the SPI.

## Manually instrumenting

> :warning: starting with 0.6.0, and prior to version 1.0.0, `opentelemetry-javaagent-all.jar`
only supports manual instrumentation using the `opentelemetry-api` version with the same version
number as the Java agent you are using. Starting with 1.0.0, the Java agent will start supporting
multiple (1.0.0+) versions of `opentelemetry-api`.

You'll need to add a dependency on the `opentelemetry-api` library to get started; if you intend to
use the `@WithSpan` annotation, also include the `opentelemetry-extension-annotations` dependency.

### Maven

```xml
  <dependencies>
    <dependency>
      <groupId>io.opentelemetry</groupId>
      <artifactId>opentelemetry-api</artifactId>
      <version>0.11.0</version>
    </dependency>
    <dependency>
      <groupId>io.opentelemetry</groupId>
      <artifactId>opentelemetry-extension-annotations</artifactId>
      <version>0.11.0</version>
    </dependency>
  </dependencies>
```

### Gradle

```groovy
dependencies {
    implementation('io.opentelemetry:opentelemetry-api:0.11.0')
    implementation('io.opentelemetry:opentelemetry-extension-annotations:0.11.0')
}
```

### Adding attributes to the current span

A common need when instrumenting an application is to capture additional application-specific or
business-specific information as additional attributes to an existing span from the automatic
instrumentation. Grab the current span with `Span.current()` and use the `setAttribute()`
methods:

```java
import io.opentelemetry.api.trace.Span;

// ...

Span span = Span.current();
span.setAttribute(..., ...);
```

### Creating spans around methods with `@WithSpan`

Another common situation is to capture a span around an existing first-party code method. The
`@WithSpan` annotation makes this straightforward:

```java
import io.opentelemetry.extension.annotations.WithSpan;

public class MyClass {
  @WithSpan
  public void MyLogic() {
      <...>
  }
}
```

Each time the application invokes the annotated method, it creates a span that denote its duration
and provides any thrown exceptions. Unless specified as an argument to the annotation, the span name
will be `<className>.<methodName>`.

#### Suppressing `@WithSpan` instrumentation

Suppressing `@WithSpan` is useful if you have code that is over-instrumented using `@WithSpan`
and you want to suppress some of them without modifying the code.

| System property                 | Environment variable            | Purpose                                                                                                                                  |
|---------------------------------|---------------------------------|------------------------------------------------------------------------------------------------------------------------------------------|
| trace.annotated.methods.exclude | TRACE_ANNOTATED_METHODS_EXCLUDE | Suppress `@WithSpan` instrumentation for specific methods.
Format is "my.package.MyClass1[method1,method2];my.package.MyClass2[method3]" |

### Creating spans manually with a Tracer

OpenTelemetry offers a tracer to easily enable custom instrumentation throughout your application.
See the [OpenTelemetry Java
QuickStart](https://github.com/open-telemetry/opentelemetry-java/blob/master/QUICKSTART.md#tracing)
for an example of how to configure the tracer and use the Tracer, Scope and Span interfaces to
instrument your application.

## Supported libraries, frameworks, and application servers

These are the supported libraries and frameworks:

| Library/Framework                                                                                                                     | Versions                       |
|---------------------------------------------------------------------------------------------------------------------------------------|--------------------------------|
| [Akka HTTP](https://doc.akka.io/docs/akka-http/current/index.html)                                                                    | 10.0+                          |
| [Apache HttpAsyncClient](https://hc.apache.org/index.html)                                                                            | 4.1+                           |
| [Apache HttpClient](https://hc.apache.org/index.html)                                                                                 | 2.0+                           |
| [Armeria](https://armeria.dev)                                                                                                        | 1.3+                           |
| [AsyncHttpClient](https://github.com/AsyncHttpClient/async-http-client)                                                               | 1.9+ (not including 2.x yet)   |
| [AWS Lambda](https://docs.aws.amazon.com/lambda/latest/dg/java-handler.html)                                                          | 1.0+                           |
| [AWS SDK](https://aws.amazon.com/sdk-for-java/)                                                                                       | 1.11.x and 2.2.0+              |
| [Cassandra Driver](https://github.com/datastax/java-driver)                                                                           | 3.0+                           |
| [Couchbase Client](https://github.com/couchbase/couchbase-java-client)                                                                | 2.0+ (not including 3.x yet)   |
| [Dropwizard Views](https://www.dropwizard.io/en/latest/manual/views.html)                                                             | 0.7+                           |
| [Elasticsearch API](https://www.elastic.co/guide/en/elasticsearch/client/java-api/current/index.html)                                 | 5.0+ (not including 7.x yet)   |
| [Elasticsearch REST Client](https://www.elastic.co/guide/en/elasticsearch/client/java-rest/current/index.html)                        | 5.0+                           |
| [Finatra](https://github.com/twitter/finatra)                                                                                         | 2.9+                           |
| [Geode Client](https://geode.apache.org/)                                                                                             | 1.4+                           |
| [Google HTTP Client](https://github.com/googleapis/google-http-java-client)                                                           | 1.19+                          |
| [Grizzly](https://javaee.github.io/grizzly/httpserverframework.html)                                                                  | 2.0+ (disabled by default)     |
| [gRPC](https://github.com/grpc/grpc-java)                                                                                             | 1.5+                           |
| [Hibernate](https://github.com/hibernate/hibernate-orm)                                                                               | 3.3+                           |
| [HttpURLConnection](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/net/HttpURLConnection.html)                     | Java 7+                        |
| [http4k <sup>&dagger;</sup>](https://www.http4k.org/guide/modules/opentelemetry/)                                                     | 3.270.0+                       |
| [Hystrix](https://github.com/Netflix/Hystrix)                                                                                         | 1.4+                           |
| [JAX-RS](https://javaee.github.io/javaee-spec/javadocs/javax/ws/rs/package-summary.html)                                              | 0.5+                           |
| [JAX-RS Client](https://javaee.github.io/javaee-spec/javadocs/javax/ws/rs/client/package-summary.html)                                | 2.0+                           |
| [JDBC](https://docs.oracle.com/en/java/javase/11/docs/api/java.sql/java/sql/package-summary.html)                                     | Java 7+                        |
| [Jedis](https://github.com/xetorthio/jedis)                                                                                           | 1.4+                           |
| [JMS](https://javaee.github.io/javaee-spec/javadocs/javax/jms/package-summary.html)                                                   | 1.1+                           |
| [JSP](https://javaee.github.io/javaee-spec/javadocs/javax/servlet/jsp/package-summary.html)                                           | 2.3+                           |
| [Kafka](https://kafka.apache.org/20/javadoc/overview-summary.html)                                                                    | 0.11+                          |
| [khttp](https://khttp.readthedocs.io)                                                                                                 | 0.1+                           |
| [Kubernetes Client](https://github.com/kubernetes-client/java)                                                                        | 7.0+                           |
| [Lettuce](https://github.com/lettuce-io/lettuce-core)                                                                                 | 4.0+ (not including 6.x yet)   |
| [Log4j 1](https://logging.apache.org/log4j/1.2/)                                                                                      | 1.2+                           |
| [Log4j 2](https://logging.apache.org/log4j/2.x/)                                                                                      | 2.7+                           |
| [Logback](http://logback.qos.ch/)                                                                                                     | 1.0+                           |
| [Mojarra](https://projects.eclipse.org/projects/ee4j.mojarra)                                                                         | 1.2+ (not including 3.x yet)   |
| [MongoDB Drivers](https://mongodb.github.io/mongo-java-driver/)                                                                       | 3.3+                           |
| [MyFaces](https://myfaces.apache.org/)                                                                                                | 1.2+ (not including 3.x yet)   |
| [Netty](https://github.com/netty/netty)                                                                                               | 3.8+                           |
| [OkHttp](https://github.com/square/okhttp/)                                                                                           | 3.0+                           |
| [Play](https://github.com/playframework/playframework)                                                                                | 2.3+ (not including 2.8.x yet) |
| [Play WS](https://github.com/playframework/play-ws)                                                                                   | 1.0+                           |
| [RabbitMQ Client](https://github.com/rabbitmq/rabbitmq-java-client)                                                                   | 2.7+                           |
| [Ratpack](https://github.com/ratpack/ratpack)                                                                                         | 1.4+                           |
| [Reactor](https://github.com/reactor/reactor-core)                                                                                    | 3.1+                           |
| [Reactor Netty](https://github.com/reactor/reactor-netty)                                                                             | 0.9+ (not including 1.0)       |
| [Rediscala](https://github.com/etaty/rediscala)                                                                                       | 1.8+                           |
| [Redisson](https://github.com/redisson/redisson)                                                                                      | 3.0+                           |
| [RMI](https://docs.oracle.com/en/java/javase/11/docs/api/java.rmi/java/rmi/package-summary.html)                                      | Java 7+                        |
| [RxJava](https://github.com/ReactiveX/RxJava)                                                                                         | 1.0+                           |
| [Servlet](https://javaee.github.io/javaee-spec/javadocs/javax/servlet/package-summary.html)                                           | 2.2+ (not including 5.x yet)   |
| [Spark Web Framework](https://github.com/perwendel/spark)                                                                             | 2.3+                           |
| [Spring Batch](https://spring.io/projects/spring-batch)                                                                               | 3.0+                           |
| [Spring Data](https://spring.io/projects/spring-data)                                                                                 | 1.8+                           |
| [Spring Scheduling](https://docs.spring.io/spring/docs/current/javadoc-api/org/springframework/scheduling/package-summary.html)       | 3.1+                           |
| [Spring Web MVC](https://docs.spring.io/spring/docs/current/javadoc-api/org/springframework/web/servlet/mvc/package-summary.html)     | 3.1+                           |
| [Spring Webflux](https://docs.spring.io/spring/docs/current/javadoc-api/org/springframework/web/reactive/package-summary.html)        | 5.0+                           |
| [Spring Web Services](https://spring.io/projects/spring-ws)                                                                           | 2.0+                           |
| [Spymemcached](https://github.com/couchbase/spymemcached)                                                                             | 2.12+                          |
| [Struts2](https://github.com/apache/struts)                                                                                           | 2.3+                           |
| [Twilio](https://github.com/twilio/twilio-java)                                                                                       | 6.6+ (not including 8.x yet)   |
| [Vert.x](https://vertx.io)                                                                                                            | 3.0+                           |
| [Vert.x RxJava2](https://vertx.io/docs/vertx-rx/java2/)                                                                               | 3.5+                           |

<sup>&dagger;</sup> OpenTelemetry support provided by the library

These are the supported application servers:

| Application server                                                                        | Version                     | JVM            | OS                             |
| ----------------------------------------------------------------------------------------- | --------------------------- | -------------- | ------------------------------ |
| [Glassfish](https://javaee.github.io/glassfish/)                                          | 5.0.x, 5.1.x                | OpenJDK 8, 11  | Ubuntu 18, Windows Server 2019 |
| [JBoss EAP](https://www.redhat.com/en/technologies/jboss-middleware/application-platform) | 7.1.x, 7.3.x                | OpenJDK 8, 11  | Ubuntu 18, Windows Server 2019 |
| [Jetty](https://www.eclipse.org/jetty/)                                                   | 9.4.x, 10.0.x               | OpenJDK 8, 11  | Ubuntu 20                      |
| [Payara](https://www.payara.fish/)                                                        | 5.0.x, 5.1.x                | OpenJDK 8, 11  | Ubuntu 18, Windows Server 2019 |
| [Tomcat](http://tomcat.apache.org/)                                                       | 7.0.x, 8.5.x, 9.0.x         | OpenJDK 8, 11  | Ubuntu 18                      |
| [Weblogic](https://www.oracle.com/java/weblogic/)                                         | 12                          | OpenJDK 8      | Oracle Linux 7, 8              |
| [Weblogic](https://www.oracle.com/java/weblogic/)                                         | 14                          | OpenJDK 8, 11  | Oracle Linux 7, 8              |
| [WildFly](https://www.wildfly.org/)                                                       | 13.0.x                      | OpenJDK 8      | Ubuntu 18, Windows Server 2019 |
| [WildFly](https://www.wildfly.org/)                                                       | 17.0.1, 21.0.0              | OpenJDK 8, 11  | Ubuntu 18, Windows Server 2019 |

### Disabled instrumentations

Some instrumentations can produce too many spans and make traces very noisy.
For this reason, the following instrumentations are disabled by default:
- `jdbc-datasource` which creates spans whenever the `java.sql.DataSource#getConnection` method is called.

To enable them, add the `otel.instrumentation.<name>.enabled` system property:
`-Dotel.instrumentation.jdbc-datasource.enabled=true`

#### Grizzly instrumentation

When you use
[Grizzly](https://javaee.github.io/grizzly/httpserverframework.html) for
Servlet-based applications, you get better experience from Servlet-specific
support. As these two instrumentations conflict with each other, more generic
instrumentation for Grizzly HTTP server is disabled by default. If needed,
you can enable it by adding the following system property:
`-Dotel.instrumentation.grizzly.enabled=true`

### Suppressing specific auto-instrumentation

See [Suppressing specific auto-instrumentation](docs/suppressing-instrumentation.md)

### Logger MDC auto-instrumentation

See [Logger MDC auto-instrumentation](docs/logger-mdc-instrumentation.md)

## Troubleshooting

To turn on the agent's internal debug logging:

`-Dotel.javaagent.debug=true`

**Note**: These logs are extremely verbose. Enable debug logging only when needed.
Debug logging negatively impacts the performance of your application.

## Roadmap to 1.0 (GA)

See [GA Requirements](docs/ga-requirements.md)

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

Approvers ([@open-telemetry/java-instrumentation-approvers](https://github.com/orgs/open-telemetry/teams/java-instrumentation-approvers)):

- [John Watson](https://github.com/jkwatson), Splunk
- [Mateusz Rzeszutek](https://github.com/mateuszrzeszutek), Splunk
- [Pavol Loffay](https://github.com/pavolloffay), Traceable.ai

Maintainers ([@open-telemetry/java-instrumentation-maintainers](https://github.com/orgs/open-telemetry/teams/java-instrumentation-maintainers)):

- [Anuraag Agrawal](https://github.com/anuraaga), AWS
- [Nikita Salnikov-Tarnovski](https://github.com/iNikem), Splunk
- [Trask Stalnaker](https://github.com/trask), Microsoft
- [Tyler Benson](https://github.com/tylerbenson), DataDog

Learn more about roles in the [community repository](https://github.com/open-telemetry/community/blob/master/community-membership.md).

Thanks to all the people who already contributed!

<a href="https://github.com/open-telemetry/opentelemetry-java-instrumentation/graphs/contributors">
  <img src="https://contributors-img.web.app/image?repo=open-telemetry/opentelemetry-java-instrumentation" />
</a>
