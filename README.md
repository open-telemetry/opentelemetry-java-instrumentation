# OpenTelemetry Instrumentation for Java

## Join the discussions!

* Watch this repo :eye:
* Join the [Gitter channel](https://gitter.im/open-telemetry/opentelemetry-java-instrumentation)
* Join the [weekly meeting](https://github.com/open-telemetry/community#java-instrumentation)

## Introduction

This project provides a Java agent JAR that can be attached to any Java 7+
application and dynamically injects bytecode to capture telemetry from a
number of popular libraries and frameworks.
The telemetry data can be exported in a variety of formats.
In addition, the agent and exporter can be configured via command line arguments
or environment variables. The net result is the ability to gather telemetry
data from a Java application without code changes.

## Getting Started

Download the [latest version](https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-auto-all.jar).

This package includes the instrumentation agent,
instrumentations for all supported libraries and all available data exporters.
This provides completely automatic out of the box experience.

The instrumentation agent is enabled using the `-javaagent` flag to the JVM.
```
java -javaagent:path/to/opentelemetry-auto-all.jar \
     -jar myapp.jar
```
By default OpenTelemetry Java agent uses
[OTLP exporter](https://github.com/open-telemetry/opentelemetry-java/tree/master/exporters/otlp)
configured to send data to
[OpenTelemetry collector](https://github.com/open-telemetry/opentelemetry-collector/blob/master/receiver/otlpreceiver/README.md)
at `localhost:55680`.

Configuration parameters are passed as Java system properties (`-D` flags) or
as environment variables (see below for full list). For example:
```
java -javaagent:path/to/opentelemetry-auto-all.jar \
     -Dota.exporter=zipkin
     -jar myapp.jar
```

### Configuration parameters (subject to change!)

Note: These parameter names are very likely to change over time, so please check
back here when trying out a new version! Please report any bugs or unexpected
behavior you may find.

#### Jaeger exporter

A simple wrapper for the Jaeger exporter of opentelemetry-java. It currently
only supports gRPC as its communications protocol.

| System property                  | Environment variable             | Purpose                                                              |
|----------------------------------|----------------------------------|----------------------------------------------------------------------|
| ota.exporter=jaeger              | OTA_EXPORTER=jaeger              | To select Jaeger exporter                                            |
| ota.exporter.jaeger.endpoint     | OTA_EXPORTER_JAEGER_ENDPOINT     | The Jaeger endpoint to connect to. Currently only gRPC is supported. |
| ota.exporter.jaeger.service.name | OTA_EXPORTER_JAEGER_SERVICE_NAME | The service name of this JVM instance                                |

#### Zipkin exporter
A simple wrapper for the Zipkin exporter of opentelemetry-java. It POSTs json in [Zipkin format](https://zipkin.io/zipkin-api/#/default/post_spans) to a specified HTTP URL.

| System property                  | Environment variable             | Purpose                                                              |
|----------------------------------|----------------------------------|----------------------------------------------------------------------|
| ota.exporter=zipkin              | OTA_EXPORTER=zipkin              | To select Zipkin exporter                                            |
| ota.exporter.zipkin.endpoint     | OTA_EXPORTER_ZIPKIN_ENDPOINT     | The Zipkin endpoint to connect to. Currently only HTTP is supported. |
| ota.exporter.zipkin.service.name | OTA_EXPORTER_ZIPKIN_SERVICE_NAME | The service name of this JVM instance

#### OTLP exporter

A simple wrapper for the OTLP exporter of opentelemetry-java.

| System property                  | Environment variable             | Purpose                                                              |
|----------------------------------|----------------------------------|----------------------------------------------------------------------|
| ota.exporter=otlp (default)      | OTA_EXPORTER=otlp                | To select OpenTelemetry exporter (default)                           |
| ota.exporter.jar                 | OTA_EXPORTER_JAR                 | Path to the exporter fat-jar that you want to use                    |
| ota.exporter.otlp.endpoint       | OTA_EXPORTER_OTLP_ENDPOINT       | The OTLP endpoint to connect to.                                     |

#### Logging Exporter

The logging exporter simply prints the name of the span along with its
attributes to stdout. It is used mainly for testing and debugging.

| System property             | Environment variable        | Purpose                                                                      |
|-----------------------------|-----------------------------|------------------------------------------------------------------------------|
| ota.exporter=logging        | OTA_EXPORTER=logging        | To select logging exporter                                                   |
| ota.exporter.logging.prefix | OTA_EXPORTER_LOGGING_PREFIX | An optional string that is printed in front of the span name and attributes. |

##### Customizing the OpenTelemetry SDK

*This is highly advanced behavior and still in the prototyping phase. It may change drastically or be removed completely. Use
with caution*

The OpenTelemetry API exposes SPI [hooks](https://github.com/open-telemetry/opentelemetry-java/blob/master/api/src/main/java/io/opentelemetry/trace/spi/TracerProviderFactory.java)
for customizing its behavior, such as the `Resource` attached to spans or the `Sampler`.

Because the auto instrumentation runs in a separate classpath than the instrumented application, it is not possible for customization in the application to take advantage of this customization. In order to provide such customization, you can
provide the path to a JAR file including an SPI implementation using the system property `ota.initializer.jar`. Note that this JAR will need to shade the OpenTelemetry API in the same way as the agent does. The simplest way to do this is to use the same shading configuration as the agent from [here](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/cfade733b899a2f02cfec7033c6a1efd7c54fd8b/java-agent/java-agent.gradle#L39). In addition, you will have to specify the `io.opentelemetry.auto.shaded.io.opentelemetry.trace.spi.TraceProvider` to the name of the class that implements the SPI.

## Supported Java libraries and frameworks

| Library/Framework                                                                                                                     | Versions                       |
|---------------------------------------------------------------------------------------------------------------------------------------|--------------------------------|
| [Akka HTTP](https://doc.akka.io/docs/akka-http/current/index.html)                                                                    | 10.0+                          |
| [Apache HttpAsyncClient](https://hc.apache.org/index.html)                                                                            | 4.0+                           |
| [Apache HttpClient](https://hc.apache.org/index.html)                                                                                 | 2.0+                           |
| [AWS SDK](https://aws.amazon.com/sdk-for-java/)                                                                                       | 1.11.x and 2.2.0+              |
| [Cassandra Driver](https://github.com/datastax/java-driver)                                                                           | 3.0+ (not including 4.x yet)   |
| [Couchbase Client](https://github.com/couchbase/couchbase-java-client)                                                                | 2.0+ (not including 3.x yet)   |
| [Dropwizard Views](https://www.dropwizard.io/en/latest/manual/views.html)                                                             | 0.7+                           |
| [Elasticsearch API](https://www.elastic.co/guide/en/elasticsearch/client/java-api/current/index.html)                                 | 2.0+ (not including 7.x yet)   |
| [Elasticsearch REST Client](https://www.elastic.co/guide/en/elasticsearch/client/java-rest/current/index.html)                        | 5.0+                           |
| [Finatra](https://github.com/twitter/finatra)                                                                                         | 2.9+                           |
| [Geode Client](https://geode.apache.org/)                                                                                             | 1.4+                           |
| [Google HTTP Client](https://github.com/googleapis/google-http-java-client)                                                           | 1.19+                          |
| [Grizzly](https://javaee.github.io/grizzly/httpserverframework.html)                                                                  | 2.0+ (disabled by default, see below) |
| [Grizzly Client](https://github.com/javaee/grizzly-ahc)                                                                               | 1.9+                           |
| [gRPC](https://github.com/grpc/grpc-java)                                                                                             | 1.5+                           |
| [Hibernate](https://github.com/hibernate/hibernate-orm)                                                                               | 3.3+                           |
| [HttpURLConnection](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/net/HttpURLConnection.html)                     | Java 7+                        |
| [Hystrix](https://github.com/Netflix/Hystrix)                                                                                         | 1.4+                           |
| [java.util.logging](https://docs.oracle.com/en/java/javase/11/docs/api/java.logging/java/util/logging/package-summary.html)           | Java 7+                        |
| [JAX-RS](https://javaee.github.io/javaee-spec/javadocs/javax/ws/rs/package-summary.html)                                              | 0.5+                           |
| [JAX-RS Client](https://javaee.github.io/javaee-spec/javadocs/javax/ws/rs/client/package-summary.html)                                | 2.0+                           |
| [JDBC](https://docs.oracle.com/en/java/javase/11/docs/api/java.sql/java/sql/package-summary.html)                                     | Java 7+                        |
| [Jedis](https://github.com/xetorthio/jedis)                                                                                           | 1.4+                           |
| [Jetty](https://www.eclipse.org/jetty/)                                                                                               | 8.0+                           |
| [JMS](https://javaee.github.io/javaee-spec/javadocs/javax/jms/package-summary.html)                                                   | 1.1+                           |
| [JSP](https://javaee.github.io/javaee-spec/javadocs/javax/servlet/jsp/package-summary.html)                                           | 2.3+                           |
| [Kafka](https://kafka.apache.org/20/javadoc/overview-summary.html)                                                                    | 0.11+                          |
| [khttp](https://khttp.readthedocs.io)                                                                                                 | 0.1.0+                         |
| [Lettuce](https://github.com/lettuce-io/lettuce-core)                                                                                 | 4.0+                           |
| [Log4j](https://logging.apache.org/log4j/2.x/)                                                                                        | 1.1+                           |
| [Logback](https://github.com/qos-ch/logback)                                                                                          | 1.0+                           |
| [MongoDB Drivers](https://mongodb.github.io/mongo-java-driver/)                                                                       | 3.3+                           |
| [Netty](https://github.com/netty/netty)                                                                                               | 3.8+                           |
| [OkHttp](https://github.com/square/okhttp/)                                                                                           | 3.0+                           |
| [Play](https://github.com/playframework/playframework)                                                                                | 2.3+ (not including 2.8.x yet) |
| [Play WS](https://github.com/playframework/play-ws)                                                                                   | 1.0+                           |
| [RabbitMQ Client](https://github.com/rabbitmq/rabbitmq-java-client)                                                                   | 2.7+                           |
| [Ratpack](https://github.com/ratpack/ratpack)                                                                                         | 1.5+                           |
| [Reactor](https://github.com/reactor/reactor-core)                                                                                    | 3.1+                           |
| [Rediscala](https://github.com/etaty/rediscala)                                                                                       | 1.8+                           |
| [RMI](https://docs.oracle.com/en/java/javase/11/docs/api/java.rmi/java/rmi/package-summary.html)                                      | Java 7+                        |
| [RxJava](https://github.com/ReactiveX/RxJava)                                                                                         | 1.0+                           |
| [Servlet](https://javaee.github.io/javaee-spec/javadocs/javax/servlet/package-summary.html)                                           | 2.2+                           |
| [Spark Web Framework](https://github.com/perwendel/spark)                                                                             | 2.3+                           |
| [Spring Data](https://spring.io/projects/spring-data)                                                                                 | 1.8+                           |
| [Spring Scheduling](https://docs.spring.io/spring/docs/current/javadoc-api/org/springframework/scheduling/package-summary.html)       | 3.1+                           |
| [Spring Web MVC](https://docs.spring.io/spring/docs/current/javadoc-api/org/springframework/web/servlet/mvc/package-summary.html)     | 3.1+                           |
| [Spring Webflux](https://docs.spring.io/spring/docs/current/javadoc-api/org/springframework/web/reactive/package-summary.html)        | 5.0+                           |
| [Spymemcached](https://github.com/couchbase/spymemcached)                                                                             | 2.12+                          |
| [Twilio](https://github.com/twilio/twilio-java)                                                                                       | 6.6+                           |
| [Vert.x](https://vertx.io)                                                                                                            | 3.0+                           |
| [Vert.x RxJava2](https://vertx.io/docs/vertx-rx/java2/)                                                                               | 3.5+                           |

### Disabled instrumentations

Some instrumentations can produce too many spans and make traces very noisy.
For this reason the following instrumentations are disabled by default:
- `jdbc-datasource` which creates spans whenever `java.sql.DataSource#getConnection` method is called.
- `servlet-filter` which creates spans around Servlet Filter methods.
- `servlet-service` which creates spans around Servlet methods.

To enable them, add `ota.integration.<name>.enabled` system property:
`-Dota.integration.jdbc-datasource.enabled=true`

#### Grizzly instrumentation

Whenever you use
[Grizzly](https://javaee.github.io/grizzly/httpserverframework.html) for
Servlet-based applications, you get better experience from Servlet-specific
support. As these two instrumentations conflict with each other, more generic
instrumentation for Grizzly http server is disabled by default. If needed,
you can enable it by add the following system property:
`-Dota.integration.grizzly.enabled=true`

## Manually instrumenting

You can use the OpenTelemetry `getTracer` or the `@WithSpan` annotation to
manually instrument your Java application.

### Configure the OpenTelemetry getTracer

OpenTelemetry offers a tracer to easily enable custom instrumentation
throughout your application. See the [OpenTelemetry Java
QuickStart](https://github.com/open-telemetry/opentelemetry-java/blob/master/QUICKSTART.md#tracing)
for an example of how to configure it.

### Configure a WithSpan annotation

If you want to configure custom instrumentation and don't want to use the
OpenTelemetry `getTracer` and API directly, configure a `@WithSpan`
annotation. Add the trace annotation to your application's code:

```java
import io.opentelemetry.contrib.auto.annotations.WithSpan;

public class MyClass {
  @WithSpan
  public void MyLogic() {
      <...>
  }
}
```

Each time the application invokes the annotated method, it creates a span
that denote its duration and provides any thrown exceptions.

#### Configuration

The `@WithSpan` annotation requires code changes to implement. You can
disable the annotation at runtime via the exclude configuration or
environment variables:

| System property                  | Environment variable             | Purpose                                                              |
|----------------------------------|----------------------------------|----------------------------------------------------------------------|
| trace.classes.exclude            | TRACE_CLASSES_EXCLUDE            | Exclude classes with the `@WithSpan` annotation                      |
| trace.methods.exclude            | TRACE_METHODS_EXCLUDE            | Exclude methods with the `@WithSpan` annotation                      |


## Troubleshooting

To turn on the agent's internal debug logging:

`-Dio.opentelemetry.auto.slf4j.simpleLogger.defaultLogLevel=debug`

Note these logs are extremely verbose. Enable debug logging only when needed.
Debug logging negatively impacts the performance of your application.

## Roadmap to 1.0 (GA)

It is our goal to release 1.0 (GA) of the auto-instrumentation agent during the first wave of
OpenTelemetry 1.0 (GA) releases, along with as many manual instrumentation libraries as possible.

High-level roadmap:

* Conform with all OpenTelemetry specifications
  * Implement all applicable semantic attributes
    * Clearly document additional attributes not defined in specification
  * Support standard configuration properties (e.g. exporters, propagators, samplers)
  * Capture standard metrics (still TBD, e.g.
      [opentelemetry-specification#522](https://github.com/open-telemetry/opentelemetry-specification/issues/522))
  * See issues with label
    [specification](https://github.com/open-telemetry/opentelemetry-java-instrumentation/labels/specification)
* Great documentation
  * See issues with label
    [documentation](https://github.com/open-telemetry/opentelemetry-java-instrumentation/labels/documentation)
* Build out manual instrumentation libraries for existing auto-instrumentation
  * Share code and tests between manual and auto-instrumentation
  * See issue
    [#45](https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/45)
* Good support for vendors to extend the agent
  * Including ability for any user to write their own auto-instrumentation
  * See issues with label
    [packaging](https://github.com/open-telemetry/opentelemetry-java-instrumentation/labels/packaging)
* Better smoke test harness and more smoke tests
  * See issue
    [#298](https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/298)
* Benchmarking and tuning (both runtime and startup)
* Address sporadic test failures
  * See issues with label
    [sporadic test failure](https://github.com/open-telemetry/opentelemetry-java-instrumentation/labels/sporadic%20test%20failure)
* Speed up CI build feedback
