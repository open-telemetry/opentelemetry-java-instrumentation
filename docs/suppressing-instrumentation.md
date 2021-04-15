## Disabling the agent entirely

You can disable the agent using `-Dotel.javaagent.enabled=false`
(or using the equivalent environment variable `OTEL_JAVAAGENT_ENABLED=false`).

## Suppressing specific agent instrumentation

You can suppress agent instrumentation of specific libraries by using
`-Dotel.instrumentation.[name].enabled=false` where `name` is the instrumentation `name`
(main or additional):

| Instrumentation name |
|---------------------------|
| akka-actor|
| akka-http|
| apache-camel|
| apache-dubbo|
| apache-httpasyncclient|
| apache-httpclient|
| armeria|
| async-http-client|
| aws-lambda|
| aws-sdk|
| axis2|
| cassandra|
| classloader |
| couchbase|
| cxf|
| dropwizard-views |
| eclipse-osgi |
| elasticsearch-rest|
| elasticsearch-transport|
| executor |
| external-annotations |
| finatra|
| geode|
| google-http-client|
| grails|
| grizzly|
| grpc|
| guava|
| gwt|
| hibernate|
| http-url-connection |
| httpclient |
| hystrix|
| jaxrs-client|
| jaxrs|
| jaxws|
| jdbc |
| jdbc-datasource |
| jedis|
| jetty|
| jms|
| jsp |
| kafka |
| khttp |
| kotlinx-coroutines |
| kubernetes-client|
| lettuce|
| liberty |
| log4j|
| logback|
| methods |
| metro|
| mojarra|
| mongo |
| myfaces|
| netty|
| okhttp|
| opentelemetry-annotations |
| opentelemetry-api |
| opentelemetry-metrics-api |
| oshi |
| play-ws|
| play|
| rabbitmq|
| ratpack|
| reactor-netty|
| reactor|
| rediscala|
| rmi|
| rocketmq-client|
| rxjava2 |
| scala-executors |
| servlet|
| spark|
| spring-core|
| spring-data|
| spring-scheduling|
| spring-webflux|
| spring-webmvc|
| spring-ws|
| spymemcached|
| struts|
| tapestry|
| tomcat|
| twilio|
| undertow|
| vaadin|
| vertx |
| wicket|

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
