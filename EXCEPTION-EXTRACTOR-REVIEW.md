# Exception Event Extractor Review Checklist

Findings from reviewing `7f8e5ec..HEAD`.

---

## 1. Bug: GWT build missing `classpath`

- [ ] `instrumentation/gwt-2.0/javaagent/build.gradle.kts` — add `classpath = sourceSets.test.get().runtimeClasspath` to the `testExceptionSignalLogs` task

---

## 2. Missing Exception Event Extractors

Instrumentations that build `Instrumenter` instances but do not call `Experimental.setExceptionEventExtractor()`.

### Controller/Handler spans (INTERNAL, under HTTP server spans)

- [ ] `instrumentation/finatra-2.9/javaagent` — `FinatraSingletons.java`
- [ ] `instrumentation/grails-3.0/javaagent` — `GrailsSingletons.java`
- [ ] `instrumentation/jaxws/jaxws-metro-2.2/javaagent` — `MetroSingletons.java`
- [ ] `instrumentation/jaxws/jaxws-cxf-3.0/javaagent` — `CxfSingletons.java`
- [ ] `instrumentation/jaxws/jaxws-common/javaagent` — `JaxWsInstrumenterFactory.java`
- [ ] `instrumentation/jaxws/jaxws-2.0-axis2-1.6/javaagent` — `Axis2Singletons.java`
- [ ] `instrumentation/jaxrs/jaxrs-common/javaagent` — `JaxrsInstrumenterFactory.java`
- [ ] `instrumentation/jaxrs/jaxrs-1.0/javaagent` — `JaxrsSingletons.java`
- [ ] `instrumentation/play/play-mvc/play-mvc-2.6/javaagent` — `Play26Singletons.java`
- [ ] `instrumentation/play/play-mvc/play-mvc-2.4/javaagent` — `Play24Singletons.java`
- [ ] `instrumentation/jsf/jsf-myfaces-3.0/javaagent` — `MyFacesSingletons.java`
- [ ] `instrumentation/jsf/jsf-myfaces-1.2/javaagent` — `MyFacesSingletons.java`
- [ ] `instrumentation/jsf/jsf-mojarra-3.0/javaagent` — `MojarraSingletons.java`
- [ ] `instrumentation/jsf/jsf-mojarra-1.2/javaagent` — `MojarraSingletons.java`
- [ ] `instrumentation/jfinal-3.2/javaagent` — `JFinalSingletons.java`
- [ ] `instrumentation/tapestry-5.4/javaagent` — `TapestrySingletons.java`
- [ ] `instrumentation/struts/struts-7.0/javaagent` — `StrutsSingletons.java`
- [ ] `instrumentation/struts/struts-2.3/javaagent` — `StrutsSingletons.java`
- [ ] `instrumentation/ratpack/ratpack-1.4/javaagent` — `RatpackSingletons.java`
- [ ] `instrumentation/spring/spring-webmvc/spring-webmvc-common/javaagent` — `SpringWebMvcInstrumenterFactory.java`
- [ ] `instrumentation/spring/spring-ws-2.0/javaagent` — `SpringWsSingletons.java`
- [ ] `instrumentation/servlet/servlet-common/javaagent` — `ResponseInstrumenterFactory.java`

### View/Render spans

- [ ] `instrumentation/dropwizard/dropwizard-views-0.7/javaagent` — `DropwizardSingletons.java`
- [ ] `instrumentation/jsp-2.3/javaagent` — `JspCompilationContextInstrumentationSingletons.java`
- [ ] `instrumentation/jsp-2.3/javaagent` — `HttpJspPageInstrumentationSingletons.java`

### Code/Method tracing

- [ ] `instrumentation/external-annotations/javaagent` — `ExternalAnnotationSingletons.java`
- [ ] `instrumentation/methods/javaagent` — `MethodSingletons.java`
- [ ] `instrumentation/mybatis-3.2/javaagent` — `MyBatisSingletons.java`
- [ ] `instrumentation/spring/spring-data/spring-data-1.8/javaagent` — `SpringDataSingletons.java`

### GraphQL

- [ ] `instrumentation/graphql-java/graphql-java-common/library` — `OpenTelemetryInstrumentationHelper.java`
- [ ] `instrumentation/graphql-java/graphql-java-20.0/library` — `GraphqlInstrumenterFactory.java`

### Batch Processing

- [ ] `instrumentation/spring/spring-batch-3.0/javaagent` — `StepSingletons.java`
- [ ] `instrumentation/spring/spring-batch-3.0/javaagent` — `ItemSingletons.java`

### Vaadin (4 instrumenters)

- [ ] `instrumentation/vaadin-14.2/javaagent` — `VaadinSingletons.java` (CLIENT_CALLABLE, REQUEST_HANDLER, RPC, SERVICE)

### Netty connection/SSL sub-instrumenters

- [ ] `instrumentation/netty/netty-3.8/javaagent` — `NettyClientSingletons.java` (CONNECTION_INSTRUMENTER)
- [ ] `instrumentation/netty/netty-common-4.0/library` — `NettyClientInstrumenterFactory.java` (connection + SSL)

---

## 3. Missing `testExceptionSignalLogs` Gradle Tasks

Modules that call `setExceptionEventExtractor` but have no `testExceptionSignalLogs` task.

### Direct modules

- [x] `instrumentation/hystrix-1.4/javaagent/build.gradle.kts`
- [x] `instrumentation/apache-elasticjob-3.0/javaagent/build.gradle.kts`
- [x] `instrumentation/camel-2.20/javaagent/build.gradle.kts`
- [x] `instrumentation/powerjob-4.0/javaagent/build.gradle.kts`
- [x] `instrumentation/quartz-2.0/library/build.gradle.kts`
- [x] `instrumentation/aws-lambda/aws-lambda-core-1.0/library/build.gradle.kts`
- [x] `instrumentation/aws-lambda/aws-lambda-events-common-2.2/library/build.gradle.kts`
- [x] `instrumentation/opentelemetry-extension-annotations-1.0/javaagent/build.gradle.kts`
- [x] `instrumentation/opentelemetry-instrumentation-annotations-1.16/javaagent/build.gradle.kts`
- [x] `instrumentation/kotlinx-coroutines/kotlinx-coroutines-1.0/javaagent/build.gradle.kts`
- [x] `instrumentation/spring/spring-batch-3.0/javaagent/build.gradle.kts`
- [x] `instrumentation/spring/spring-scheduling-3.1/javaagent/build.gradle.kts`
- [x] `instrumentation/spring/spring-webflux/spring-webflux-5.0/javaagent/build.gradle.kts`
- [x] `instrumentation/spring/spring-boot-autoconfigure/build.gradle.kts`

### Common modules (+ their leaf modules)

- [x] `instrumentation/kafka/kafka-clients/kafka-clients-common-0.11/library/build.gradle.kts`
- [x] `instrumentation/kafka/kafka-connect-2.6/javaagent/build.gradle.kts`
- [x] `instrumentation/hibernate/hibernate-common/javaagent/build.gradle.kts` + leaf modules (4.0, 6.0)
- [x] `instrumentation/jms/jms-common/javaagent/build.gradle.kts` + leaf modules
- [x] `instrumentation/elasticsearch/elasticsearch-transport-common/javaagent/build.gradle.kts` + leaf modules
- [x] `instrumentation/elasticsearch/elasticsearch-rest-common-5.0/library/build.gradle.kts` + leaf modules
- [x] `instrumentation/clickhouse/clickhouse-client-common/javaagent/build.gradle.kts` + leaf modules (v1, v2)
- [x] `instrumentation/redisson/redisson-common/javaagent/build.gradle.kts` + leaf modules
- [x] `instrumentation/opensearch/opensearch-rest-common/javaagent/build.gradle.kts` + leaf modules
- [x] `instrumentation/vertx/vertx-sql-client/vertx-sql-client-common/javaagent/build.gradle.kts` + leaf modules
- [x] `instrumentation/xxl-job/xxl-job-common/javaagent/build.gradle.kts` + leaf modules

---

## 4. Test Files Missing Conditional Exception Assertions

Tests with `.hasException(` that are not gated by `emitExceptionAsSpanEvents()` and have no `emitExceptionAsLogs()` log assertions. When `-Dotel.semconv.exception.signal.opt-in=logs` is active, these will fail.

### DB tests

- [x] `instrumentation/clickhouse/clickhouse-client-v2-0.8/javaagent/src/test/…/ClickHouseClientV2Test.java`
- [x] `instrumentation/clickhouse/clickhouse-client-v1-0.5/javaagent/src/test/…/ClickHouseClientV1Test.java`
- [x] `instrumentation/hibernate/hibernate-4.0/javaagent/src/test/…/SessionTest.java`
- [x] `instrumentation/hibernate/hibernate-6.0/javaagent/src/hibernate6Test/…/SessionTest.java`
- [x] `instrumentation/hibernate/hibernate-6.0/javaagent/src/hibernate7Test/…/SessionTest.java`
- [x] `instrumentation/hibernate/hibernate-procedure-call-4.3/javaagent/src/test/…/ProcedureCallTest.java`
- [x] `instrumentation/elasticsearch/elasticsearch-transport-5.3/javaagent/src/test/…/Elasticsearch53SpringTemplateTest.java`

### HTTP tests

- [x] `instrumentation/http-url-connection/javaagent/src/test/…/HttpUrlConnectionTest.java`
- [x] `instrumentation/kubernetes-client-7.0/javaagent/src/test/…/KubernetesClientTest.java`
- [x] `instrumentation/kubernetes-client-7.0/javaagent/src/version20Test/…/KubernetesClientVer20Test.java`

### Netty connection/SSL tests

- [x] `instrumentation/netty/netty-4.1/javaagent/src/test/…/Netty41ConnectionSpanTest.java`
- [x] `instrumentation/netty/netty-4.1/javaagent/src/test/…/Netty41ClientSslTest.java`
- [x] `instrumentation/netty/netty-4.0/javaagent/src/test/…/Netty40ConnectionSpanTest.java`
- [x] `instrumentation/netty/netty-4.0/javaagent/src/test/…/Netty40ClientSslTest.java`
- [x] `instrumentation/reactor/reactor-netty/reactor-netty-1.0/javaagent/src/test/…/ReactorNettyConnectionSpanTest.java`
- [x] `instrumentation/reactor/reactor-netty/reactor-netty-1.0/javaagent/src/test/…/ReactorNettyClientSslTest.java`
- [x] `instrumentation/reactor/reactor-netty/reactor-netty-0.9/javaagent/src/test/…/ReactorNettyConnectionSpanTest.java`

### Reactor Netty HTTP tests

- [x] `instrumentation/reactor/reactor-netty/reactor-netty-1.0/javaagent/src/test/…/AbstractReactorNettyHttpClientTest.java`
- [x] `instrumentation/reactor/reactor-netty/reactor-netty-0.9/javaagent/src/test/…/AbstractReactorNettyHttpClientTest.java`

### Framework handler tests

- [x] `instrumentation/grails-3.0/javaagent/src/test/…/GrailsTest.java`
- [x] `instrumentation/jfinal-3.2/javaagent/src/test/…/JFinalTest.java`
- [x] `instrumentation/tapestry-5.4/javaagent/src/test/…/TapestryTest.java`
- [x] `instrumentation/struts/struts-7.0/javaagent/src/test/…/Struts2ActionSpanTest.java`
- [x] `instrumentation/struts/struts-2.3/javaagent/src/test/…/Struts2ActionSpanTest.java`
- [x] `instrumentation/dropwizard/dropwizard-testing/src/test/…/DropwizardTest.java`
- [x] `instrumentation/play/play-mvc/play-mvc-2.6/javaagent/src/test/…/PlayServerTest.java`
- [x] `instrumentation/play/play-mvc/play-mvc-2.6/javaagent/src/latestDepTest/…/PlayServerTest.java`
- [x] `instrumentation/play/play-mvc/play-mvc-2.4/javaagent/src/test/…/PlayServerTest.java`
- [x] `instrumentation/play/play-mvc/play-mvc-2.4/javaagent/src/play24Test/…/PlayServerTest.java`
- [x] `instrumentation/spring/spring-webmvc/spring-webmvc-3.1/wildfly-testing/src/test/…/AbstractOpenTelemetryHandlerMappingFilterTest.java`

### Scheduled job tests

- [x] `instrumentation/apache-elasticjob-3.0/javaagent/src/test/…/ElasticJobTest.java`
- [x] `instrumentation/spring/spring-batch-3.0/javaagent/src/test/…/SpringBatchTest.java`

### Annotation/tracing tests

- [x] `instrumentation/external-annotations/javaagent/src/test/…/TraceAnnotationsTest.java`
- [x] `instrumentation/opentelemetry-extension-annotations-1.0/javaagent/src/test/…/WithSpanInstrumentationTest.java`
- [x] `instrumentation/methods/javaagent/src/test/…/MethodTest.java`
- [x] `instrumentation/rxjava/rxjava-2.0/javaagent/src/test/…/BaseRxJava2WithSpanTest.java`

### AWS Lambda tests

- [x] `instrumentation/aws-lambda/aws-lambda-core-1.0/library/src/test/…/AwsLambdaStreamWrapperTest.java`
- [x] `instrumentation/aws-lambda/aws-lambda-core-1.0/library/src/test/…/AwsLambdaStreamWrapperHttpPropagationTest.java`
- [x] `instrumentation/aws-lambda/aws-lambda-core-1.0/javaagent/src/test/…/AwsLambdaStreamHandlerTest.java`
- [x] `instrumentation/aws-lambda/aws-lambda-events-2.2/javaagent/src/test/…/AwsLambdaStreamHandlerTest.java`
- [x] `instrumentation/aws-lambda/aws-lambda-events-3.11/library/src/test/…/AwsLambdaWrapperTest.java`

### AWS SDK tests

- [x] `instrumentation/aws-sdk/aws-sdk-1.11/javaagent/src/test/…/S3ClientTest.java`
- [x] `instrumentation/aws-sdk/aws-sdk-1.11/javaagent/src/test_before_1_11_106/…/Aws0ClientTest.java`

### Other

- [x] `instrumentation/graphql-java/graphql-java-20.0/library/src/test/…/GraphqlTest.java`
- [x] `instrumentation/hystrix-1.4/javaagent/src/test/…/HystrixTest.java`
- [x] `instrumentation/hystrix-1.4/javaagent/src/test/…/HystrixObservableTest.java`
- [x] `instrumentation/opentelemetry-api/opentelemetry-api-1.0/javaagent/src/test/…/TracerTest.java`
- [x] `instrumentation/spring/spring-kafka-2.7/javaagent/src/test/…/SpringKafkaTest.java`
- [x] `instrumentation/servlet/servlet-2.2/javaagent/src/test/…/HttpServletResponseTest.java`
- [x] `instrumentation/servlet/servlet-3.0/javaagent-testing/src/test/…/HttpServletResponseTest.java`

---

## 5. Design Note: Controller exceptions lost in logs mode

`AbstractHttpServerTest.assertControllerSpan()` gates `.hasException()` with `emitExceptionAsSpanEvents()`, but no log assertion is added for controller exceptions. The `assertExceptionLogs()` call at line 797 checks for `http.server.request.exception` (the server span's event name), not the controller span's. Since the controller instrumenters in section 2 don't have extractors, controller exceptions are silently dropped in logs mode. Decide whether this is acceptable or if controller-level extractors are needed.
