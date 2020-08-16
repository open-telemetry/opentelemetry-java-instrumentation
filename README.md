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

Download the [latest version](https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent-all.jar).

This package includes the instrumentation agent,
instrumentations for all supported libraries and all available data exporters.
This provides completely automatic out of the box experience.

The instrumentation agent is enabled using the `-javaagent` flag to the JVM.
```
java -javaagent:path/to/opentelemetry-javaagent-all.jar \
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
java -javaagent:path/to/opentelemetry-javaagent-all.jar \
     -Dotel.exporter=zipkin
     -jar myapp.jar
```

External exporter jar can be specified via `otel.exporter.jar` system property:
```
java -javaagent:path/to/opentelemetry-javaagent-all.jar \
     -Dotel.exporter.jar=path/to/external-exporter.jar
     -jar myapp.jar
```

### Configuration parameters (subject to change!)

Note: These parameter names are very likely to change over time, so please check
back here when trying out a new version! Please report any bugs or unexpected
behavior you may find.

#### Jaeger exporter

A simple wrapper for the Jaeger exporter of opentelemetry-java. It currently
only supports gRPC as its communications protocol.

| System property          | Environment variable     | Purpose                                                                                            |
|--------------------------|--------------------------|----------------------------------------------------------------------------------------------------|
| otel.exporter=jaeger     | OTEL_EXPORTER=jaeger     | To select Jaeger exporter                                                                          |
| otel.jaeger.endpoint     | OTEL_JAEGER_ENDPOINT     | The Jaeger endpoint to connect to, default is "localhost:14250", currently only gRPC is supported. |
| otel.jaeger.service.name | OTEL_JAEGER_SERVICE_NAME | The service name of this JVM instance, default is "unknown".                                       |

#### Zipkin exporter
A simple wrapper for the Zipkin exporter of opentelemetry-java. It POSTs json in [Zipkin format](https://zipkin.io/zipkin-api/#/default/post_spans) to a specified HTTP URL.

| System property          | Environment variable     | Purpose                                                                                                               |
|--------------------------|--------------------------|-----------------------------------------------------------------------------------------------------------------------|
| otel.exporter=zipkin     | OTEL_EXPORTER=zipkin     | To select Zipkin exporter                                                                                             |
| otel.zipkin.endpoint     | OTEL_ZIPKIN_ENDPOINT     | The Zipkin endpoint to connect to, default is "http://localhost:9411/api/v2/spans". Currently only HTTP is supported. |
| otel.zipkin.service.name | OTEL_ZIPKIN_SERVICE_NAME | The service name of this JVM instance, default is "unknown".                                                          |

#### OTLP exporter

A simple wrapper for the OTLP exporter of opentelemetry-java.

| System property                  | Environment variable             | Purpose                                                                 |
|----------------------------------|----------------------------------|-------------------------------------------------------------------------|
| otel.exporter=otlp (default)     | OTEL_EXPORTER=otlp               | To select OpenTelemetry exporter (default)                              |
| otel.otlp.endpoint               | OTEL_OTLP_ENDPOINT               | The OTLP endpoint to connect to, default is "localhost:55680"           |
| otel.otlp.use.tls                | OTEL_OTLP_USE_TLS                | To use or not TLS, default is false.                                    |
| otel.otlp.metadata               | OTEL_OTLP_METADATA               | The key-value pairs separated by semicolon to pass as request metadata. |
| otel.otlp.span.timeout           | OTEL_OTLP_SPAN_TIMEOUT           | The max waiting time allowed to send each span batch, default is 1000.  |

In order to configure the service name for the OTLP exporter, you must add `service.name` key
to the OpenTelemetry Resource ([see below](#opentelemetry-resource)), e.g. `OTEL_RESOURCE_ATTRIBUTES=service.name=myservice`.

#### Logging exporter

The logging exporter simply prints the name of the span along with its
attributes to stdout. It is used mainly for testing and debugging.

| System property              | Environment variable         | Purpose                                                                      |
|------------------------------|------------------------------|------------------------------------------------------------------------------|
| otel.exporter=logging        | OTEL_EXPORTER=logging        | To select logging exporter                                                   |
| otel.exporter.logging.prefix | OTEL_EXPORTER_LOGGING_PREFIX | An optional string that is printed in front of the span name and attributes. |

#### Propagator

The propagator controls which distributed tracing header format is used.

If this is set to a comma-delimited list of the values, the multi-propagator will be used.
The multi-propagator will try to extract the context from incoming requests using each of the configured propagator formats (in order), stopping after the first successful context extraction.
The multi-propagator will inject the context into outgoing requests using all the configured propagator formats.

| System property  | Environment variable | Purpose                                                                                                     |
|------------------|----------------------|-------------------------------------------------------------------------------------------------------------|
| otel.propagators | OTEL_PROPAGATORS     | Default is "tracecontext" (W3C). Other supported values are "b3", "b3single", "jaeger", "ottracer", "xray". |

#### OpenTelemetry Resource

The [OpenTelemetry Resource](https://github.com/open-telemetry/opentelemetry-specification/blob/master/specification/resource/sdk.md)
is a representation of the entity producing telemetry.

| System property          | Environment variable     | Purpose                                                                      |
|--------------------------|--------------------------|------------------------------------------------------------------------------|
| otel.resource.attributes | OTEL_RESOURCE_ATTRIBUTES | Used to specify resource attributes in format: key1=val1,key2=val2,key3=val3 |

#### Peer service name

The [peer service name](https://github.com/open-telemetry/opentelemetry-specification/blob/master/specification/trace/semantic_conventions/span-general.md#general-remote-service-attributes) is
the name of a remote service that is being connected to. It corresponds to `service.name` in the [Resource](https://github.com/open-telemetry/opentelemetry-specification/tree/master/specification/resource/semantic_conventions#service) for the local service.

| System property                     | Environment variable              | Purpose                                                                      |
|------------------------------------|------------------------------------|------------------------------------------------------------------------------|
| otel.endpoint.peer.service.mapping | OTEL_ENDPOINT_PEER_SERVICE_MAPPING | Used to specify a mapping from hostnames or IP addresses to peer services, as a comma separated list of host=name pairs. The peer service name will be added as an attribute to a span whose host or IP match the mapping. For example, if set to 1.2.3.4=cats-service,dogs-abcdef123.serverlessapis.com=dogs-api, requests to `1.2.3.4` will have a `peer.service` attribute of `cats-service` and requests to `dogs-abcdef123.serverlessapis.com` will have one of `dogs-api` |

#### Batch span processor

| System property           | Environment variable      | Purpose                                                                      |
|---------------------------|---------------------------|------------------------------------------------------------------------------|
| otel.bsp.schedule.delay   | OTEL_BSP_SCHEDULE_DELAY   | The interval in milliseconds between two consecutive exports (default: 5000) |
| otel.bsp.max.queue        | OTEL_BSP_MAX_QUEUE        | Maximum queue size (default: 2048)                                           |
| otel.bsp.max.export.batch | OTEL_BSP_MAX_EXPORT_BATCH | Maximum batch size (default: 512)                                            |
| otel.bsp.export.timeout   | OTEL_BSP_EXPORT_TIMEOUT   | Maximum allowed time in milliseconds to export data (default: 30000)         |
| otel.bsp.export.sampled   | OTEL_BSP_EXPORT_SAMPLED   | Whether only sampled spans should be exported (default: true)                |

#### Trace config

| System property                 | Environment variable            | Purpose                                              |
|---------------------------------|---------------------------------|------------------------------------------------------|
| otel.config.sampler.probability | OTEL_CONFIG_SAMPLER_PROBABILITY | Sampling probability between 0 and 1 (default: 1)    |
| otel.config.max.attrs           | OTEL_CONFIG_MAX_ATTRS           | Maximum number of attributes per span (default: 32)  |
| otel.config.max.events          | OTEL_CONFIG_MAX_EVENTS          | Maximum number of events per span (default: 128)     |
| otel.config.max.links           | OTEL_CONFIG_MAX_LINKS           | Maximum number of links per span (default: 32)       |
| otel.config.max.event.attrs     | OTEL_CONFIG_MAX_EVENT_ATTRS     | Maximum number of attributes per event (default: 32) |
| otel.config.max.link.attrs      | OTEL_CONFIG_MAX_LINK_ATTRS      | Maximum number of attributes per link (default: 32)  |

#### Interval metric reader

| System property          | Environment variable     | Purpose                                                                      |
|--------------------------|--------------------------|------------------------------------------------------------------------------|
| otel.imr.export.interval | OTEL_IMR_EXPORT_INTERVAL | The interval in milliseconds between pushes to the exporter (default: 60000) |

##### Customizing the OpenTelemetry SDK

*This is highly advanced behavior and still in the prototyping phase. It may change drastically or be removed completely. Use
with caution*

The OpenTelemetry API exposes SPI [hooks](https://github.com/open-telemetry/opentelemetry-java/blob/master/api/src/main/java/io/opentelemetry/trace/spi/TracerProviderFactory.java)
for customizing its behavior, such as the `Resource` attached to spans or the `Sampler`.

Because the auto instrumentation runs in a separate classpath than the instrumented application, it is not possible for customization in the application to take advantage of this customization. In order to provide such customization, you can
provide the path to a JAR file including an SPI implementation using the system property `otel.initializer.jar`. Note that this JAR will need to shade the OpenTelemetry API in the same way as the agent does. The simplest way to do this is to use the same shading configuration as the agent from [here](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/cfade733b899a2f02cfec7033c6a1efd7c54fd8b/java-agent/java-agent.gradle#L39). In addition, you will have to specify the `io.opentelemetry.javaagent.shaded.io.opentelemetry.trace.spi.TraceProvider` to the name of the class that implements the SPI.

## Supported Java libraries and frameworks

| Library/Framework                                                                                                                     | Versions                       |
|---------------------------------------------------------------------------------------------------------------------------------------|--------------------------------|
| [Akka HTTP](https://doc.akka.io/docs/akka-http/current/index.html)                                                                    | 10.0+                          |
| [Apache HttpAsyncClient](https://hc.apache.org/index.html)                                                                            | 4.0+                           |
| [Apache HttpClient](https://hc.apache.org/index.html)                                                                                 | 2.0+                           |
| [Armeria](https://armeria.dev)                                                                                                        | 0.99.8+                        |
| [AWS SDK](https://aws.amazon.com/sdk-for-java/)                                                                                       | 1.11.x and 2.2.0+              |
| [Cassandra Driver](https://github.com/datastax/java-driver)                                                                           | 3.0+                           |
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
| [JAX-RS](https://javaee.github.io/javaee-spec/javadocs/javax/ws/rs/package-summary.html)                                              | 0.5+                           |
| [JAX-RS Client](https://javaee.github.io/javaee-spec/javadocs/javax/ws/rs/client/package-summary.html)                                | 2.0+                           |
| [JDBC](https://docs.oracle.com/en/java/javase/11/docs/api/java.sql/java/sql/package-summary.html)                                     | Java 7+                        |
| [Jedis](https://github.com/xetorthio/jedis)                                                                                           | 1.4+                           |
| [Jetty](https://www.eclipse.org/jetty/)                                                                                               | 8.0+                           |
| [JMS](https://javaee.github.io/javaee-spec/javadocs/javax/jms/package-summary.html)                                                   | 1.1+                           |
| [JSP](https://javaee.github.io/javaee-spec/javadocs/javax/servlet/jsp/package-summary.html)                                           | 2.3+                           |
| [Kafka](https://kafka.apache.org/20/javadoc/overview-summary.html)                                                                    | 0.11+                          |
| [khttp](https://khttp.readthedocs.io)                                                                                                 | 0.1+                           |
| [Kubernetes Client](https://github.com/kubernetes-client/java)                                                                        | 7.0+                           |
| [Lettuce](https://github.com/lettuce-io/lettuce-core)                                                                                 | 4.0+                           |
| [MongoDB Drivers](https://mongodb.github.io/mongo-java-driver/)                                                                       | 3.3+                           |
| [Netty](https://github.com/netty/netty)                                                                                               | 3.8+                           |
| [OkHttp](https://github.com/square/okhttp/)                                                                                           | 3.0+                           |
| [Play](https://github.com/playframework/playframework)                                                                                | 2.3+ (not including 2.8.x yet) |
| [Play WS](https://github.com/playframework/play-ws)                                                                                   | 1.0+                           |
| [RabbitMQ Client](https://github.com/rabbitmq/rabbitmq-java-client)                                                                   | 2.7+                           |
| [Ratpack](https://github.com/ratpack/ratpack)                                                                                         | 1.4+                           |
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

To enable them, add `otel.integration.<name>.enabled` system property:
`-Dotel.integration.jdbc-datasource.enabled=true`

#### Grizzly instrumentation

Whenever you use
[Grizzly](https://javaee.github.io/grizzly/httpserverframework.html) for
Servlet-based applications, you get better experience from Servlet-specific
support. As these two instrumentations conflict with each other, more generic
instrumentation for Grizzly http server is disabled by default. If needed,
you can enable it by add the following system property:
`-Dotel.integration.grizzly.enabled=true`

### Suppressing specific auto-instrumentation

See [Suppressing specific auto-instrumentation](docs/suppressing-instrumentation.md)

## Manually instrumenting

> :warning: starting with 0.6.0, and prior to version 1.0.0, `opentelemetry-javaagent-all.jar`
only supports manual instrumentation using the `opentelemetry-api` version with the same version
number as the Java agent you are using. Starting with 1.0.0, the Java agent will start supporting
multiple (1.0.0+) versions of `opentelemetry-api`.

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
import io.opentelemetry.extensions.auto.annotations.WithSpan;

public class MyClass {
  @WithSpan
  public void MyLogic() {
      <...>
  }
}
```

Each time the application invokes the annotated method, it creates a span
that denote its duration and provides any thrown exceptions.

#### Suppressing `@WithSpan` instrumentation

This is useful in case you have code that is over-instrumented using `@WithSpan`,
and you want to suppress some of them without modifying the code.

| System property                 | Environment variable            | Purpose                                                                                                                                  |
|---------------------------------|---------------------------------|------------------------------------------------------------------------------------------------------------------------------------------|
| trace.annotated.methods.exclude | TRACE_ANNOTATED_METHODS_EXCLUDE | Suppress `@WithSpan` instrumentation for specific methods, format is "my.package.MyClass1[method1,method2];my.package.MyClass2[method3]" |


## Troubleshooting

To turn on the agent's internal debug logging:

`-Dio.opentelemetry.javaagent.slf4j.simpleLogger.defaultLogLevel=debug`

Note these logs are extremely verbose. Enable debug logging only when needed.
Debug logging negatively impacts the performance of your application.

## Roadmap to 1.0 (GA)

See [GA Requirements](docs/ga-requirements.md)
