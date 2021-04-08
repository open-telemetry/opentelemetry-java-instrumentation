## Disabling the agent entirely

You can disable the agent using `-Dotel.javaagent.enabled=false`
(or using the equivalent environment variable `OTEL_JAVAAGENT_ENABLED=false`).

## Suppressing specific agent instrumentation

You can suppress agent instrumentation of specific libraries by using
`-Dotel.instrumentation.[name].enabled=false` where `name` is the instrumentation `name`
(main or additional):

| Main instrumentation name | Additional names |
|---------------------------|------------------|
| akka-actor|akka-actor-2.5 |
| akka-http|akka-http-client |
| akka-http|akka-http-server |
| apache-camel|apache-camel-2.20 |
| apache-dubbo|apache-dubbo-2.7 |
| apache-httpasyncclient|apache-httpasyncclient-4.1 |
| apache-httpclient|apache-httpclient-2.0 |
| apache-httpclient|apache-httpclient-4.0 |
| apache-httpclient|apache-httpclient-5.0 |
| armeria|armeria-1.3 |
| async-http-client|async-http-client-1.9 |
| async-http-client|async-http-client-2.0 |
| aws-lambda|aws-lambda-1.0 |
| aws-sdk|aws-sdk-1.11 |
| aws-sdk|aws-sdk-2.2 |
| axis2|axis2-1.6 |
| cassandra|cassandra-3.0 |
| cassandra|cassandra-4.0 |
| classloader |
| couchbase|couchbase-2.0 |
| couchbase|couchbase-2.6 |
| couchbase|couchbase-3.1 |
| cxf|cxf-3.0 |
| dropwizard-views |
| eclipse-osgi |
| elasticsearch-rest|elasticsearch-rest-5.0, elasticsearch |
| elasticsearch-rest|elasticsearch-rest-6.0, elasticsearch |
| elasticsearch-rest|elasticsearch-rest-7.0, elasticsearch |
| elasticsearch-transport|elasticsearch-transport-5.0, elasticsearch |
| elasticsearch-transport|elasticsearch-transport-5.3, elasticsearch |
| elasticsearch-transport|elasticsearch-transport-6.0, elasticsearch |
| executor |
| external-annotations |
| finatra|finatra-2.9 |
| geode|geode-1.4 |
| google-http-client|google-http-client-1.19 |
| grails|grails-3.0 |
| grizzly|grizzly-2.0 |
| grpc|grpc-1.5 |
| guava|guava-10.0 |
| gwt|gwt-2.0 |
| hibernate|hibernate-3.3 |
| hibernate|hibernate-4.0 |
| hibernate|hibernate-4.3 |
| http-url-connection |
| httpclient |
| hystrix|hystrix-1.4 |
| jaxrs-client|jaxrs-client-1.1 |
| jaxrs-client|jaxrs-client-2.0 |
| jaxrs-client|jaxrs-client-2.0, cxf-client, cxf-client-3.0 |
| jaxrs-client|jaxrs-client-2.0, jersey-client, jersey-client-2.0 |
| jaxrs-client|jaxrs-client-2.0, resteasy-client, resteasy-client-2.0 |
| jaxrs|jaxrs-1.0 |
| jaxrs|jaxrs-2.0 |
| jaxrs|jaxrs-2.0, cxf, cxf-3.2 |
| jaxrs|jaxrs-2.0, jersey, jersey-2.0 |
| jaxrs|jaxrs-2.0, resteasy, resteasy-3.0 |
| jaxrs|jaxrs-2.0, resteasy, resteasy-3.1 |
| jaxws|jaxws-2.0 |
| jaxws|jws-1.1 |
| jdbc |
| jdbc-datasource |
| jedis|jedis-1.4 |
| jedis|jedis-3.0 |
| jetty|jetty-11.0 |
| jetty|jetty-8.0 |
| jms|jms-1.1 |
| jsp |
| kafka-clients|kafka-clients-0.11, kafka |
| kafka-streams|kafka-streams-0.11, kafka |
| khttp |
| kotlinx-coroutines |
| kubernetes-client|kubernetes-client-7.0 |
| lettuce|lettuce-4.0 |
| lettuce|lettuce-5.0 |
| lettuce|lettuce-5.1 |
| liberty |
| liberty|liberty-dispatcher |
| log4j|log4j-1.2 |
| log4j|log4j-2.13.2 |
| log4j|log4j-2.7 |
| logback|logback-1.0 |
| methods |
| metro|metro-2.2 |
| mojarra|mojarra-1.2 |
| mongo-async|mongo-async-3.3, mongo |
| mongo|mongo-3.1 |
| mongo|mongo-3.7 |
| myfaces|myfaces-1.2 |
| netty|netty-3.8 |
| netty|netty-4.0 |
| netty|netty-4.1 |
| okhttp|okhttp-2.2 |
| okhttp|okhttp-3.0 |
| opentelemetry-annotations |
| opentelemetry-api |
| opentelemetry-metrics-api |
| oshi |
| play-ws|play-ws-1.0 |
| play-ws|play-ws-2.0 |
| play-ws|play-ws-2.1 |
| play|play-2.3 |
| play|play-2.4 |
| play|play-2.6 |
| rabbitmq|rabbitmq-2.7 |
| ratpack|ratpack-1.4 |
| reactor-netty|reactor-netty-0.9 |
| reactor-netty|reactor-netty-1.0 |
| reactor|reactor-3.1 |
| rediscala|rediscala-1.8 |
| rmi|rmi-client |
| rmi|rmi-context-propagation |
| rocketmq-client|rocketmq-client-4.8 |
| rxjava2 |
| scala-executors |
| servlet|servlet-2.2 |
| servlet|servlet-3.0 |
| servlet|servlet-5.0 |
| servlet|servlet-javax-common |
| spark|spark-2.3 |
| spring-core|spring-core-2.0 |
| spring-data|spring-data-1.8 |
| spring-scheduling|spring-scheduling-3.1 |
| spring-webflux|spring-webflux-5.0, spring-webflux-client |
| spring-webflux|spring-webflux-5.0, spring-webflux-server |
| spring-webmvc|spring-webmvc-3.1 |
| spring-ws|spring-ws-2.0 |
| spymemcached|spymemcached-2.12 |
| struts|struts-2.3 |
| tapestry|tapestry-5.4 |
| tomcat|tomcat-10.0 |
| tomcat|tomcat-7.0 |
| twilio|twilio-6.6 |
| undertow|undertow-1.4 |
| vaadin|vaadin-14.2 |
| vertx-client|vertx |
| vertx-reactive|vertx-reactive-3.5, vertx |
| vertx-web|vertx-web-3.0, vertx |
| wicket|wicket-8.0 |

*NOTE:* using a particular name in suppression config will disable *ALL* instrumentation with this name (eg `servlet` will disable all 4 instrumentations)

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
