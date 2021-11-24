
# Supported libraries, frameworks, application servers, and JVMs

We automatically instrument and support a huge number of libraries, frameworks,
and application servers... right out of the box!

Don't see your favorite tool listed here?  Consider [filing an issue](https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues),
or [contributing](../CONTRIBUTING.md).

## Contents

  * [Libraries / Frameworks](#libraries--frameworks)
  * [Application Servers](#application-servers)
  * [JVMs and Operating Systems](#jvms-and-operating-systems)
  * [Disabled instrumentations](#disabled-instrumentations)
    + [Grizzly instrumentation](#grizzly-instrumentation)

## Libraries / Frameworks

These are the supported libraries and frameworks:

| Library/Framework                                                                                                                     | Versions                       |
|---------------------------------------------------------------------------------------------------------------------------------------|--------------------------------|
| [Akka HTTP](https://doc.akka.io/docs/akka-http/current/index.html)                                                                    | 10.0+                          |
| [Apache Axis2](https://axis.apache.org/axis2/java/core/)                                                                              | 1.6+                           |
| [Apache CXF JAX-RS](https://cxf.apache.org/)                                                                                          | 3.2+                           |
| [Apache CXF JAX-RS Client](https://cxf.apache.org/)                                                                                   | 3.0+                           |
| [Apache CXF JAX-WS](https://cxf.apache.org/)                                                                                          | 3.0+                           |
| [Apache Dubbo](https://github.com/apache/dubbo/)                                                                                      | 2.7+ (not including 3.x yet)   |
| [Apache HttpAsyncClient](https://hc.apache.org/index.html)                                                                            | 4.1+                           |
| [Apache HttpClient](https://hc.apache.org/index.html)                                                                                 | 2.0+                           |
| [Apache RocketMQ](https://rocketmq.apache.org/)                                                                                       | 4.8+                           |
| [Apache Tapestry](https://tapestry.apache.org/)                                                                                       | 5.4+                           |
| [Apache Wicket](https://wicket.apache.org/)                                                                                           | 8.0+                           |
| [Armeria](https://armeria.dev)                                                                                                        | 1.3+                           |
| [AsyncHttpClient](https://github.com/AsyncHttpClient/async-http-client)                                                               | 1.9+                           |
| [AWS Lambda](https://docs.aws.amazon.com/lambda/latest/dg/java-handler.html)                                                          | 1.0+                           |
| [AWS SDK](https://aws.amazon.com/sdk-for-java/)                                                                                       | 1.11.x and 2.2.0+              |
| [Cassandra Driver](https://github.com/datastax/java-driver)                                                                           | 3.0+                           |
| [Couchbase Client](https://github.com/couchbase/couchbase-java-client)                                                                | 2.0+ and 3.1+                  |
| [Dropwizard Views](https://www.dropwizard.io/en/latest/manual/views.html)                                                             | 0.7+                           |
| [Elasticsearch API](https://www.elastic.co/guide/en/elasticsearch/client/java-api/current/index.html)                                 | 5.0+                           |
| [Elasticsearch REST Client](https://www.elastic.co/guide/en/elasticsearch/client/java-rest/current/index.html)                        | 5.0+                           |
| [Finatra](https://github.com/twitter/finatra)                                                                                         | 2.9+                           |
| [Geode Client](https://geode.apache.org/)                                                                                             | 1.4+                           |
| [Google HTTP Client](https://github.com/googleapis/google-http-java-client)                                                           | 1.19+                          |
| [Grails](https://grails.org/)                                                                                                         | 3.0+                           |
| [Grizzly](https://javaee.github.io/grizzly/httpserverframework.html)                                                                  | 2.0+ (disabled by default)     |
| [gRPC](https://github.com/grpc/grpc-java)                                                                                             | 1.6+                           |
| [GWT](http://www.gwtproject.org/)                                                                                                     | 2.0+                           |
| [Hibernate](https://github.com/hibernate/hibernate-orm)                                                                               | 3.3+                           |
| [HttpURLConnection](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/net/HttpURLConnection.html)                     | Java 8+                        |
| [http4k <sup>&dagger;</sup>](https://www.http4k.org/guide/modules/opentelemetry/)                                                     | 3.270.0+                       |
| [Hystrix](https://github.com/Netflix/Hystrix)                                                                                         | 1.4+                           |
| [JAX-RS](https://javaee.github.io/javaee-spec/javadocs/javax/ws/rs/package-summary.html)                                              | 0.5+                           |
| [JAX-RS Client](https://javaee.github.io/javaee-spec/javadocs/javax/ws/rs/client/package-summary.html)                                | 2.0+                           |
| [JAX-WS](https://jakarta.ee/specifications/xml-web-services/2.3/apidocs/javax/xml/ws/package-summary.html)                            | 2.0+ (not including 3.x yet)   |
| [Java Http Client](https://docs.oracle.com/en/java/javase/11/docs/api/java.net.http/java/net/http/package-summary.html)               | Java 11+                       |
| [JDBC](https://docs.oracle.com/en/java/javase/11/docs/api/java.sql/java/sql/package-summary.html)                                     | Java 8+                        |
| [Jedis](https://github.com/xetorthio/jedis)                                                                                           | 1.4+                           |
| [Jersey](https://eclipse-ee4j.github.io/jersey/)                                                                                      | 2.0+ (not including 3.x yet)   |
| [Jetty HTTP Client](https://www.eclipse.org/jetty/javadoc/jetty-9/org/eclipse/jetty/client/HttpClient.html)                           | 9.2+ (not including 10+ yet)   |
| [JMS](https://javaee.github.io/javaee-spec/javadocs/javax/jms/package-summary.html)                                                   | 1.1+                           |
| [JSP](https://javaee.github.io/javaee-spec/javadocs/javax/servlet/jsp/package-summary.html)                                           | 2.3+                           |
| [Kafka](https://kafka.apache.org/20/javadoc/overview-summary.html)                                                                    | 0.11+                          |
| [Kubernetes Client](https://github.com/kubernetes-client/java)                                                                        | 7.0+                           |
| [Lettuce](https://github.com/lettuce-io/lettuce-core)                                                                                 | 4.0+                           |
| [Log4j 1](https://logging.apache.org/log4j/1.2/)                                                                                      | 1.2+                           |
| [Log4j 2](https://logging.apache.org/log4j/2.x/)                                                                                      | 2.7+                           |
| [Logback](http://logback.qos.ch/)                                                                                                     | 1.0+                           |
| [Metro](https://projects.eclipse.org/projects/ee4j.metro)                                                                             | 2.2+ (not including 3.x yet)   |
| [Mojarra](https://projects.eclipse.org/projects/ee4j.mojarra)                                                                         | 1.2+ (not including 3.x yet)   |
| [MongoDB Driver](https://mongodb.github.io/mongo-java-driver/)                                                                        | 3.1+                           |
| [MyFaces](https://myfaces.apache.org/)                                                                                                | 1.2+ (not including 3.x yet)   |
| [Netty](https://github.com/netty/netty)                                                                                               | 3.8+                           |
| [OkHttp](https://github.com/square/okhttp/)                                                                                           | 3.0+                           |
| [Play](https://github.com/playframework/playframework)                                                                                | 2.4+ (not including 2.8.x yet) |
| [Play WS](https://github.com/playframework/play-ws)                                                                                   | 1.0+                           |
| [Quartz](https://www.quartz-scheduler.org/)                                                                                           | 2.0+                           |
| [RabbitMQ Client](https://github.com/rabbitmq/rabbitmq-java-client)                                                                   | 2.7+                           |
| [Ratpack](https://github.com/ratpack/ratpack)                                                                                         | 1.4+                           |
| [Reactor](https://github.com/reactor/reactor-core)                                                                                    | 3.1+                           |
| [Reactor Netty](https://github.com/reactor/reactor-netty)                                                                             | 0.9+                           |
| [Rediscala](https://github.com/etaty/rediscala)                                                                                       | 1.8+                           |
| [Redisson](https://github.com/redisson/redisson)                                                                                      | 3.0+                           |
| [RESTEasy](https://resteasy.github.io/)                                                                                               | 3.0+                           |
| [RMI](https://docs.oracle.com/en/java/javase/11/docs/api/java.rmi/java/rmi/package-summary.html)                                      | Java 8+                        |
| [RxJava](https://github.com/ReactiveX/RxJava)                                                                                         | 1.0+                           |
| [Servlet](https://javaee.github.io/javaee-spec/javadocs/javax/servlet/package-summary.html)                                           | 2.2+                           |
| [Spark Web Framework](https://github.com/perwendel/spark)                                                                             | 2.3+                           |
| [Spring Batch](https://spring.io/projects/spring-batch)                                                                               | 3.0+                           |
| [Spring Data](https://spring.io/projects/spring-data)                                                                                 | 1.8+                           |
| [Spring Integration](https://spring.io/projects/spring-integration)                                                                   | 4.1+                           |
| [Spring Kafka](https://spring.io/projects/spring-kafka)                                                                               | 2.7+                           |
| [Spring Rabbit](https://spring.io/projects/spring-amqp)                                                                               | 1.0+                           |
| [Spring Scheduling](https://docs.spring.io/spring/docs/current/javadoc-api/org/springframework/scheduling/package-summary.html)       | 3.1+                           |
| [Spring Web MVC](https://docs.spring.io/spring/docs/current/javadoc-api/org/springframework/web/servlet/mvc/package-summary.html)     | 3.1+                           |
| [Spring Web Services](https://spring.io/projects/spring-ws)                                                                           | 2.0+                           |
| [Spring Webflux](https://docs.spring.io/spring/docs/current/javadoc-api/org/springframework/web/reactive/package-summary.html)        | 5.0+                           |
| [Spymemcached](https://github.com/couchbase/spymemcached)                                                                             | 2.12+                          |
| [Struts2](https://github.com/apache/struts)                                                                                           | 2.3+                           |
| [Twilio](https://github.com/twilio/twilio-java)                                                                                       | 6.6+ (not including 8.x yet)   |
| [Undertow](https://undertow.io/)                                                                                                      | 1.4+                           |
| [Vaadin](https://vaadin.com/)                                                                                                         | 14.2+                          |
| [Vert.x](https://vertx.io)                                                                                                            | 3.0+                           |
| [Vert.x RxJava2](https://vertx.io/docs/vertx-rx/java2/)                                                                               | 3.5+                           |

<sup>&dagger;</sup> OpenTelemetry support provided by the library

## Application Servers

These are the supported application servers:

| Application server                                                                        | Version                     | JVM              | OS                             |
| ----------------------------------------------------------------------------------------- | --------------------------- | ---------------- | ------------------------------ |
| [Glassfish](https://javaee.github.io/glassfish/)                                          | 5.0.x, 5.1.x                | OpenJDK 8, 11    | Ubuntu 18, Windows Server 2019 |
| [JBoss EAP](https://www.redhat.com/en/technologies/jboss-middleware/application-platform) | 7.1.x, 7.3.x                | OpenJDK 8, 11    | Ubuntu 18, Windows Server 2019 |
| [Jetty](https://www.eclipse.org/jetty/)                                                   | 9.4.x, 10.0.x, 11.0.x       | OpenJDK 8, 11    | Ubuntu 20                      |
| [Payara](https://www.payara.fish/)                                                        | 5.0.x, 5.1.x                | OpenJDK 8, 11    | Ubuntu 18, Windows Server 2019 |
| [Tomcat](http://tomcat.apache.org/)                                                       | 7.0.x, 8.5.x, 9.0.x, 10.0.x | OpenJDK 8, 11    | Ubuntu 18                      |
| [TomEE](https://tomee.apache.org/)                                                        | 7.x, 8.x                    | OpenJDK 8, 11    | Ubuntu 18                      |
| [Weblogic](https://www.oracle.com/java/weblogic/)                                         | 12.x                        | Oracle JDK 8     | Oracle Linux 7, 8              |
| [Weblogic](https://www.oracle.com/java/weblogic/)                                         | 14.x                        | Oracle JDK 8, 11 | Oracle Linux 7, 8              |
| [Websphere Liberty Profile](https://www.ibm.com/cloud/websphere-liberty)                  | 20.x, 21.x                  | OpenJDK 8, 11    | Ubuntu 18, Windows Server 2019 |
| [Websphere Traditional](https://www.ibm.com/cloud/websphere-application-server)           | 8.5.5.x, 9.0.x              | IBM JDK 8        | Red Hat Enterprise Linux 8.4   |
| [WildFly](https://www.wildfly.org/)                                                       | 13.x                        | OpenJDK 8        | Ubuntu 18, Windows Server 2019 |
| [WildFly](https://www.wildfly.org/)                                                       | 17.x, 21.x, 25.x            | OpenJDK 8, 11    | Ubuntu 18, Windows Server 2019 |

## JVMs and operating systems

These are the supported JVM version and OS configurations which the javaagent is tested on:

| JVM                                               | Versions  | OS                             |
| ------------------------------------------------- | --------- | ------------------------------ |
| [AdoptOpenJDK Hotspot](https://adoptopenjdk.net/) | 8, 11, 15 | Ubuntu 18, Windows Server 2019 |
| [AdoptOpenJDK OpenJ9](https://adoptopenjdk.net/)  | 8, 11, 15 | Ubuntu 18, Windows Server 2019 |

## Disabled instrumentations

Some instrumentations can produce too many spans and make traces very noisy.
For this reason, the following instrumentations are disabled by default:

- `jdbc-datasource` which creates spans whenever the `java.sql.DataSource#getConnection` method is called.

To enable them, add the `otel.instrumentation.<name>.enabled` system property:
`-Dotel.instrumentation.jdbc-datasource.enabled=true`

### Grizzly instrumentation

When you use
[Grizzly](https://javaee.github.io/grizzly/httpserverframework.html) for
Servlet-based applications, you get better experience from Servlet-specific
support. As these two instrumentations conflict with each other, more generic
instrumentation for Grizzly HTTP server is disabled by default. If needed,
you can enable it by adding the following system property:
`-Dotel.instrumentation.grizzly.enabled=true`
