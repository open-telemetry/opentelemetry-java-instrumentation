## Disabling the agent entirely

You can disable the agent using `-Dotel.javaagent.enabled=false`
(or using the equivalent environment variable `OTEL_JAVAAGENT_ENABLED=false`).

## Suppressing specific agent instrumentation

You can suppress agent instrumentation of specific libraries by using
`-Dotel.instrumentation.[name].enabled=false` where `name` is the corresponding instrumentation `name`:

| Library/Framework | Instrumentation name |
|-------------------|----------------------|
| Additional methods tracing | methods |
| Additional tracing annotations | external-annotations |
| Akka Actor | akka-actor|
| Akka HTTP | akka-http|
| Apache Axis2 | axis2|
| Apache Camel | apache-camel|
| Apache Cassandra | cassandra|
| Apache CXF | cxf|
| Apache Dubbo | apache-dubbo|
| Apache Geode | geode|
| Apache HttpAsyncClient | apache-httpasyncclient|
| Apache HttpClient | apache-httpclient|
| Apache Kafka | kafka |
| Apache RocketMQ | rocketmq-client|
| Apache Tapestry | tapestry|
| Apache Tomcat | tomcat|
| Apache Wicket | wicket|
| Armeria | armeria|
| AsyncHttpClient (AHC) | async-http-client|
| AWS Lambda | aws-lambda|
| AWS SDK | aws-sdk|
| Couchbase | couchbase|
| Dropwizard Views | dropwizard-views |
| Eclipse OSGi | eclipse-osgi |
| Elasticsearch client | elasticsearch-transport|
| Elasticsearch REST client | elasticsearch-rest|
| Google Guava | guava|
| Google HTTP client | google-http-client|
| Google Web Toolkit | gwt|
| Grails | grails|
| GRPC | grpc|
| Hibernate | hibernate|
| Java EE Grizzly | grizzly|
| Java HTTP Client | java-http-client |
| Java `HttpURLConnection` | http-url-connection |
| Java JDBC | jdbc |
| Java JDBC `DataSource` | jdbc-datasource |
| Java RMI | rmi|
| Java Servlet | servlet|
| java.util.concurrent | executor |
| JAX-RS (Client) | jaxrs-client|
| JAX-RS (Server) | jaxrs|
| JAX-WS | jaxws|
| JAX-WS Metro | metro|
| Jetty | jetty|
| JMS | jms|
| JSF Mojarra | mojarra|
| JSF MyFaces | myfaces|
| JSP | jsp |
| K8s Client | kubernetes-client|
| Kotlin HTTP (kHttp) | khttp |
| kotlinx.coroutines | kotlinx-coroutines |
| Log4j | log4j|
| Logback | logback|
| MongoDB | mongo |
| Netflix Hystrix | hystrix|
| Netty | netty|
| OkHttp | okhttp|
| OpenLiberty | liberty |
| OpenTelemetry Trace annotations | opentelemetry-annotations |
| OSHI (Operating System and Hardware Information) | oshi |
| Play Framework | play|
| Play WS HTTP Client | play-ws|
| RabbitMQ Client | rabbitmq|
| Ratpack | ratpack|
| ReactiveX RxJava | rxjava2, rxjava3 |
| Reactor | reactor|
| Reactor Netty | reactor-netty|
| Redis Jedis | jedis|
| Redis Lettuce | lettuce|
| Rediscala | rediscala|
| Scala executors | scala-executors |
| Spark Web Framework | spark|
| Spring Core | spring-core|
| Spring Data | spring-data|
| Spring Scheduling | spring-scheduling|
| Spring Webflux | spring-webflux|
| Spring WebMVC | spring-webmvc|
| Spring WS | spring-ws|
| Spymemcached | spymemcached|
| Struts | struts|
| Twilio SDK | twilio|
| Twitter Finatra | finatra|
| Undertow | undertow|
| Vaadin | vaadin|
| Vert.x RxJava2 | vertx |

### Even more fine-grained control

You can also exclude specific classes from being instrumented.

This can be useful to completely silence spans from a given class/package.

Or as a quick workaround for an instrumentation bug, when byte code in one specific class is problematic.

This option should not be used lightly, as it can leave some instrumentation partially applied,
which could have unknown side-effects.

If you find yourself needing to use this, it would be great if you could drop us an issue explaining why,
so that we can try to come up with a better solution to address your need.

| System property                | Environment variable           | Purpose                                                                                           |
|--------------------------------|--------------------------------|---------------------------------------------------------------------------------------------------|
| otel.javaagent.exclude-classes | OTEL_JAVAAGENT_EXCLUDE_CLASSES | Suppresses all instrumentation for specific classes, format is "my.package.MyClass,my.package2.*" |

## Enable manual instrumentation only

You can suppress all auto instrumentations but have support for manual instrumentation with `@WithSpan` and normal API interactions by using
`-Dotel.instrumentation.common.default-enabled=false -Dotel.instrumentation.opentelemetry-annotations.enabled=true`

## Enable instrumentation suppression by type

By default the agent suppresses all nested `CLIENT` instrumentations, without discerning their actual type (e.g. a database `CLIENT` using an HTTP client that generates `CLIENT` spans too).
By setting `-Dotel.instrumentation.experimental.outgoing-span-suppression-by-type=true` you can enable a more sophisticated suppression strategy: only `CLIENT` spans of the same types (e.g. DB, HTTP, RPC) will be suppressed.
