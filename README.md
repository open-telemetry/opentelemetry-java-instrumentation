# OpenTelemetry Auto-Instrumentation for Java

## Notice - Work in Progress!
*This project is still in the early phases of development and is not yet stable.* You are welcome to experiment with it, 
but we strongly discourage any production use!

## Introduction
This project uses a technique known as bytecode instrumentation to add tracing to a Java application.
Simply put, we provide a Java agent that can be attached to any Java 7+ application and dynamically adds code to enable tracing. 

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
| [Grizzly](https://javaee.github.io/grizzly/httpserverframework.html)                                                                  | 2.0+                           |
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
| [Lettuce](https://github.com/lettuce-io/lettuce-core)                                                                                 | 5.0+                           |
| [Log4j](https://logging.apache.org/log4j/2.x/)                                                                                        | 1.1+                           |
| [Logback](https://github.com/qos-ch/logback)                                                                                          | 1.0+                           |
| [MongoDB Drivers](https://mongodb.github.io/mongo-java-driver/)                                                                       | 3.3+                           |
| [Netty](https://github.com/netty/netty)                                                                                               | 4.0+                           |
| [OkHttp](https://github.com/square/okhttp/)                                                                                           | 3.0+                           |
| [Play](https://github.com/playframework/playframework)                                                                                | 2.4+ (not including 2.8.x yet) |
| [Play WS](https://github.com/playframework/play-ws)                                                                                   | 1.0+                           |
| [Project Reactor](https://github.com/reactor/reactor-core)                                                                            | 3.1+                           |
| [RabbitMQ Client](https://github.com/rabbitmq/rabbitmq-java-client)                                                                   | 2.7+                           |
| [Ratpack](https://github.com/ratpack/ratpack)                                                                                         | 1.4+                           |
| [RMI](https://docs.oracle.com/en/java/javase/11/docs/api/java.rmi/java/rmi/package-summary.html)                                      | Java 7+                        |
| [RxJava](https://github.com/ReactiveX/RxJava)                                                                                         | 1.0+                           |
| [Servlet](https://javaee.github.io/javaee-spec/javadocs/javax/servlet/package-summary.html)                                           | 2.3+                           |
| [Spark Web Framework](https://github.com/perwendel/spark)                                                                             | 2.3+                           |
| [Spring Data](https://spring.io/projects/spring-data)                                                                                 | 1.8+                           |
| [Spring Scheduling](https://docs.spring.io/spring/docs/current/javadoc-api/org/springframework/scheduling/package-summary.html)       | 3.1+                           |
| [Spring Servlet MVC](https://docs.spring.io/spring/docs/current/javadoc-api/org/springframework/web/servlet/mvc/package-summary.html) | 3.1+                           |
| [Spring Webflux](https://docs.spring.io/spring/docs/current/javadoc-api/org/springframework/web/reactive/package-summary.html)        | 5.0+                           |
| [Spymemcached](https://github.com/couchbase/spymemcached)                                                                             | 2.12+                          |
| [Twilio](https://github.com/twilio/twilio-java)                                                                                       | 6.6+                           |


## Using the agent
As mentioned above, this project is in a very early phase of development and not yet stable. 
However, you can try it on your Java application by following these instructions.

### Download and run

Download the [latest release](https://github.com/open-telemetry/opentelemetry-auto-instr-java/releases).

The instrumentation agent is enabled using the -javaagent flag to the JVM. Configuration parameters are passed 
as Java system properties (-D flags) or as environment variables. This is an example:

```
java -javaagent:/path/to/opentelemetry-auto-<version>.jar \
     -Dota.exporter.jar=exporter-adapters/logging-exporter-adapter/build/libs/logging-exporter-adapter-0.1.2-SNAPSHOT.jar \
     -Dota.jaeger.host=localhost \
     -Dota.jaeger.port=14250 \
     -Dota.service=shopping \
     -jar myapp.jar
```

### Configuration parameters (subject to change!)
System property | Environment variable | Purpose
--- | --- | ---
ota.exporter.jar | OTA_EXPORTER_JAR | The path to an exporter JAR
ota.service | OTA_SERVICE | The service name of this JVM instance. This is used as a label in Jaeger to distinguish between JVM instances in a multi-service environment.

### Available exporters
Currently two exporters are available and bundled with this project. They area available under the ```exporter-adapters``` directory. 

#### Logging Exporter
The logging exporter simply prints the name of the span along with its attributes to stdout. It is used manly
for testing and debugging. It takes a single configuration parameter.

System property | Environment variable | Purpose
--- | --- | ---
ota.exporter.logging.prefix | OTA_EXPORTER_LOGGING_PREFIX | A string that is printed in front of the span name and attributes.

#### Jaeger exporter
A simple wrapper for the Jaeger exporter of opentelemetry-java. It currently only supports gRPC as its communications protocol.

System property | Environment variable | Purpose
--- | --- | ---
ota.exporter.jaeger.host | OTA_EXPORTER_JAEGER_HOST | The Jaeger host to connect to. Currently only gRPC is supported.
ota.exporter.jaeger.port | OTA_EXPORTER_JAEGER_PORT | The port to connect to on the Jaeger host. Currently only gRPC is supported

These parameter names are very likely to change over time, so please check back here when trying out a new version!

Please report any bugs or unexpected behavior you may find.

## Building from source

Build using Java 8:

```gradle assemble```

and then you can find the java agent artifact at `java-agent/build/lib/opentelemetry-auto-<version>.jar`.
