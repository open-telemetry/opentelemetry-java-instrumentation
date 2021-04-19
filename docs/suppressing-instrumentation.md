## Disabling the agent entirely

You can disable the agent using `-Dotel.javaagent.enabled=false`
(or using the equivalent environment variable `OTEL_JAVAAGENT_ENABLED=false`).

## Suppressing specific agent instrumentation

You can suppress agent instrumentation of specific libraries by using
`-Dotel.instrumentation.[name].enabled=false` where `name` is the corresponding instrumentation `name`:

| Library/Framework | Instrumentation name |
|-------------------|----------------------|
| Akka Actor | akka-actor|
| Akka HTTP | akka-http|
| Apache Camel | apache-camel|
| Apache Dubbo | apache-dubbo|
| Apache HttpAsyncClient | apache-httpasyncclient|
| Apache HttpClient | apache-httpclient|
| Armeria | armeria|
| AsyncHttpClient (AHC) | async-http-client|
| AWS Lambda | aws-lambda|
| AWS SDK | aws-sdk|
| Apache Axis2 | axis2|
| Apache Cassandra | cassandra|
| Couchbase | couchbase|
| Apache CXF | cxf|
| Dropwizard Views | dropwizard-views |
| Eclipse OSGi | eclipse-osgi |
| Elasticsearch REST client | elasticsearch-rest|
| Elasticsearch client | elasticsearch-transport|
| java.util.concurrent | executor |
| Additional tracing annotations | external-annotations |
| Twitter Finatra | finatra|
| Apache Geode | geode|
| Google HTTP client | google-http-client|
| Grails | grails|
| Java EE Grizzly | grizzly|
| GRPC | grpc|
| Google Guava | guava|
| Google Web Toolkit | gwt|
| Hibernate | hibernate|
| Java HTTP URL connection | http-url-connection |
| Java HTTP Client | httpclient |
| Netflix Hystrix | hystrix|
| JAX-RS Clieny | jaxrs-client|
| JAX-RS | jaxrs|
| JAX-WS | jaxws|
| Java JDBC | jdbc |
| Java JDBC data source | jdbc-datasource |
| Redis Jedis | jedis|
| Jetty | jetty|
| JMS | jms|
| JSP | jsp |
| Apache Kafka | kafka |
| Kotlin HTTP (kHttp) | khttp |
| kotlinx.coroutines | kotlinx-coroutines |
| K8s Client | kubernetes-client|
| Lettuce (Redis) | lettuce|
| OpenLiberty | liberty |
| Log4j | log4j|
| Logback | logback|
| Additional methods tracing | methods |
| JAX-WS Metro | metro|
| JSF Mojarra | mojarra|
| MongoDB | mongo |
| JSF MyFaces | myfaces|
| Netty | netty|
| OkHttp | okhttp|
| OpenTelemetry Trace annotations | opentelemetry-annotations |
| OSHI (Operating System and Hardware Information) | oshi |
| Play WS HTTP Client | play-ws|
| Play Framework | play|
| RabbitMQ Client | rabbitmq|
| Ratpack | ratpack|
| Reactor Netty | reactor-netty|
| Reactor | reactor|
| Rediscala | rediscala|
| Java RMI | rmi|
| Apache RocketMQ | rocketmq-client|
| ReactiveX RxJava | rxjava2 |
| Scala executors | scala-executors |
| Java Servlet | servlet|
| Spark Web Framework | spark|
| Spring Core | spring-core|
| Spring Data | spring-data|
| Spring Scheduling | spring-scheduling|
| Spring Webflux | spring-webflux|
| Spring WebMVC | spring-webmvc|
| Spring WS | spring-ws|
| Spymemcached | spymemcached|
| Struts | struts|
| Apache Tapestry | tapestry|
| Apache Tomcat | tomcat|
| Twilio SDK | twilio|
| Undertow | undertow|
| Vaadin | vaadin|
| Vert.x RxJava2 | vertx |
| Apache Wicket | wicket|

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
