## Disabling the agent entirely

You can disable the agent using `-Dotel.javaagent.enabled=false`
(or using the equivalent environment variable `OTEL_JAVAAGENT_ENABLED=false`).

## Suppressing specific agent instrumentation

You can suppress agent instrumentation of specific libraries by using
`-Dotel.instrumentation.[name].enabled=false` (or using the equivalent environment
variable `OTEL_INSTRUMENTATION_[NAME]_ENABLED`) where `name` (`NAME`) is the
corresponding instrumentation `name`:

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
| Apache MyFaces | myfaces|
| Apache RocketMQ | rocketmq-client|
| Apache Struts 2 | struts|
| Apache Tapestry | tapestry|
| Apache Tomcat | tomcat|
| Apache Wicket | wicket|
| Armeria | armeria|
| AsyncHttpClient (AHC) | async-http-client|
| AWS Lambda | aws-lambda|
| AWS SDK | aws-sdk|
| Couchbase | couchbase|
| Dropwizard Views | dropwizard-views |
| Eclipse Grizzly | grizzly|
| Eclipse Jersey | jersey|
| Eclipse Jetty | jetty|
| Eclipse Jetty HTTP Client | jetty-httpclient|
| Eclipse Metro | metro|
| Eclipse Mojarra | mojarra|
| Eclipse OSGi | eclipse-osgi |
| Eclipse Vert.x | vertx |
| Elasticsearch client | elasticsearch-transport|
| Elasticsearch REST client | elasticsearch-rest|
| Google Guava | guava|
| Google HTTP client | google-http-client|
| Google Web Toolkit | gwt|
| Grails | grails|
| GRPC | grpc|
| Hibernate | hibernate|
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
| JMS | jms|
| JSP | jsp |
| K8s Client | kubernetes-client|
| kotlinx.coroutines | kotlinx-coroutines |
| Log4j | log4j|
| Logback | logback|
| MongoDB | mongo |
| Netflix Hystrix | hystrix|
| Netty | netty|
| OkHttp | okhttp|
| OpenLiberty | liberty |
| OpenTelemetry Trace annotations | opentelemetry-annotations |
| OpenTelemetry API | opentelemetry-api |
| OSHI (Operating System and Hardware Information) | oshi |
| Play Framework | play|
| Play WS HTTP Client | play-ws|
| Quartz | quartz|
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
| Spring Batch | spring-batch|
| Spring Core | spring-core|
| Spring Data | spring-data|
| Spring Integration | spring-integration|
| Spring Kafka | spring-kafka|
| Spring RabbitMQ | spring-rabbit|
| Spring Scheduling | spring-scheduling|
| Spring Web | spring-web|
| Spring WebFlux | spring-webflux|
| Spring Web MVC | spring-webmvc|
| Spring Web Services | spring-ws|
| Spymemcached | spymemcached|
| Twilio SDK | twilio|
| Twitter Finatra | finatra|
| Undertow | undertow|
| Vaadin | vaadin|

**Note:** When using environment variables, dashes (`-`) should be converted to
underscores (`_`). For example, to suppress traces from `akka-actor` library, set
`OTEL_INSTRUMENTATION_AKKA_ACTOR_ENABLED` to `false`.

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

Some of the libraries that this agent instruments in turn use lower-level libraries, that are also instrumented.
This results in nested `CLIENT` spans (a span with the kind `CLIENT` has a child span with the same kind `CLIENT`).
For example spans produced by Reactor Netty instrumentation will have children spans produced by Netty instrumentation.
Or Dynamo DB spans produced by AWS SDK instrumentation will have children spans produced by http protocol library instrumentation.

Although OpenTelemetry specification allows such situation, such nested spans often produce duplicate data without any added benefit.
For this reason this agent by default suppresses nested `CLIENT` spans and emits only the top-level one.

By setting `-Dotel.instrumentation.experimental.outgoing-span-suppression-by-type=true` you can enable a more sophisticated suppression strategy: only `CLIENT` spans of the same semantic convention type (e.g. DB, HTTP, RPC) will be suppressed.
For example, if we have a database client which uses Reactor Netty http client which uses Netty networking library, then without any suppression we would have 3 nested spans:

- `CLIENT` span with database semantic attributes from the database client instrumentation
- `CLIENT` span with http semantic attributes from Reactor Netty instrumentation
- `CLIENT` span with http semantic attributes from Netty instrumentation

With default suppresion, we would have 1 span:

- `CLIENT` span with database semantic attributes from the database client instrumentation

With suppresion by type, we would have 2 nested spans:

- `CLIENT` span with database semantic attributes from the database client instrumentation
- `CLIENT` span with http semantic attributes from Reactor Netty instrumentation
