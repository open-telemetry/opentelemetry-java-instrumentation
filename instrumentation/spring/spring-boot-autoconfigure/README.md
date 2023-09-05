# OpenTelemetry Spring Auto-Configuration

Auto-configures OpenTelemetry instrumentation for [spring-web](../spring-web/spring-web-3.1/library)
, [spring-webmvc](../spring-webmvc/spring-webmvc-5.3/library),
and [spring-webflux](../spring-webflux/spring-webflux-5.3/library). Leverages Spring Aspect Oriented
Programming,
dependency injection, and bean post-processing to trace spring applications. To include all features
listed below use the [opentelemetry-spring-boot-starter](../starters/spring-boot-starter/README.md).

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the latest stable [release](https://search.maven.org/search?q=g:io.opentelemetry).

- Minimum version: `1.17.0`

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <!-- opentelemetry -->
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-spring-boot</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>

  <dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-api</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>

   <!-- simple span exporter -->
   <!-- outputs spans to console -->
   <!-- provides opentelemetry-sdk artifact -->
   <dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-logging</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>

</dependencies>
```

For Gradle, add to your dependencies:

```groovy
//opentelemetry spring auto-configuration
implementation("io.opentelemetry.instrumentation:opentelemetry-spring-boot:OPENTELEMETRY_VERSION")
//opentelemetry
implementation("io.opentelemetry:opentelemetry-api:OPENTELEMETRY_VERSION")
//opentelemetry exporter
implementation("io.opentelemetry:opentelemetry-exporters-otlp:OPENTELEMETRY_VERSION")
```

### Features

#### Dependencies

The following dependencies are optional but are required to use the corresponding features.

Replace `SPRING_VERSION` with the version of spring you're using.

- Minimum version: `3.1`

Replace `SPRING_WEBFLUX_VERSION` with the version of spring-webflux you're using.

- Minimum version: `5.0`

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <!-- opentelemetry exporters-->
  <dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-jaeger</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
  <dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-zipkin</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
  <dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>

  <!-- Used to autoconfigure spring-web -->
  <dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-web</artifactId>
    <version>SPRING_VERSION</version>
  </dependency>

  <!-- Used to autoconfigure spring-webmvc -->
  <dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-webmvc</artifactId>
    <version>SPRING_VERSION</version>
  </dependency>

  <!-- Used to autoconfigure spring-webflux -->
  <dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-webflux</artifactId>
    <version>SPRING_WEBFLUX_VERSION</version>
  </dependency>

  <!-- Used to enable instrumentation using @WithSpan  -->
  <dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-aop</artifactId>
    <version>SPRING_VERSION</version>
  </dependency>
  <dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-extension-annotations</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```groovy
//opentelemetry exporter
implementation("io.opentelemetry:opentelemetry-exporter-jaeger:OPENTELEMETRY_VERSION")
implementation("io.opentelemetry:opentelemetry-exporter-zipkin:OPENTELEMETRY_VERSION")
implementation("io.opentelemetry:opentelemetry-exporter-otlp:OPENTELEMETRY_VERSION")

//Used to autoconfigure spring-web
implementation("org.springframework:spring-web:SPRING_VERSION")

//Used to autoconfigure spring-webmvc
implementation("org.springframework:spring-webmvc:SPRING_VERSION")

//Used to autoconfigure spring-webflux
implementation("org.springframework:spring-webflux:SPRING_WEBFLUX_VERSION")

//Enables instrumentation using @WithSpan
implementation("org.springframework:spring-aop:SPRING_VERSION")
implementation("io.opentelemetry:opentelemetry-extension-annotations:OPENTELEMETRY_VERSION")
```

#### OpenTelemetry Auto Configuration

#### OpenTelemetry Tracer Auto Configuration

Provides a OpenTelemetry tracer bean (`io.opentelemetry.api.trace.Tracer`) if one does not exist in the application context of the spring project. This tracer bean will be used in all configurations listed below. Feel free to declare your own Opentelemetry tracer bean to overwrite this configuration.

#### Spring Web Auto Configuration

Provides auto-configuration for the OpenTelemetry RestTemplate trace interceptor defined in [opentelemetry-spring-web-3.1](../spring-web/spring-web-3.1). This auto-configuration instruments all requests sent using Spring RestTemplate beans by applying a RestTemplate bean post processor. This feature is supported for spring web versions 3.1+. [Spring Web - RestTemplate Client Span](#spring-web---resttemplate-client-span) show cases a sample client span generated by this auto-configuration. Check out [opentelemetry-spring-web-3.1](../spring-web/spring-web-3.1) to learn more about the OpenTelemetry RestTemplateInterceptor.

#### Spring Web MVC Auto Configuration

This feature autoconfigures instrumentation for Spring WebMVC controllers by adding
a [telemetry producing servlet `Filter`](../spring-webmvc/spring-webmvc-5.3/library/src/main/java/io/opentelemetry/instrumentation/spring/webmvc/v5_3/WebMvcTelemetryProducingFilter.java)
bean to the application context. This filter decorates the request execution with an OpenTelemetry
server span, propagating the incoming tracing context if received in the HTTP request. Check
out [`opentelemetry-spring-webmvc-5.3` instrumentation library](../spring-webmvc/spring-webmvc-5.3/library)
to learn more about the OpenTelemetry Spring WebMVC instrumentation.

#### Spring WebFlux Auto Configuration

Provides auto-configurations for the OpenTelemetry WebClient ExchangeFilter defined
in [opentelemetry-spring-webflux-5.3](../spring-webflux/spring-webflux-5.3). This auto-configuration
instruments all outgoing http requests sent using Spring's WebClient and WebClient Builder beans by
applying a bean post processor. This feature is supported for spring webflux versions 5.0+.
[Spring Web-Flux - WebClient Span](#spring-web-flux---webclient-span) showcases a sample span
generated by the WebClientFilter. Check
out [opentelemetry-spring-webflux-5.3](../spring-webflux/spring-webflux-5.3) to learn more about the
OpenTelemetry WebClientFilter.

#### Manual Instrumentation Support - @WithSpan

This feature uses spring-aop to wrap methods annotated with `@WithSpan` in a span. The arguments
to the method can be captured as attributed on the created span by annotating the method
parameters with `@SpanAttribute`.

Note - This annotation can only be applied to bean methods managed by the spring application
context. Check out [spring-aop](https://docs.spring.io/spring/docs/current/spring-framework-reference/core.html#aop)
to learn more about aspect weaving in spring.

##### Usage

```java
import org.springframework.stereotype.Component;

import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;

/**
 * Test WithSpan
 */
@Component
public class TracedClass {

    @WithSpan
    public void tracedMethod() {
    }

    @WithSpan(value="span name")
    public void tracedMethodWithName() {
        Span currentSpan = Span.current();
        currentSpan.addEvent("ADD EVENT TO tracedMethodWithName SPAN");
        currentSpan.setAttribute("isTestAttribute", true);
    }

    @WithSpan(kind = SpanKind.CLIENT)
    public void tracedClientSpan() {
    }

    public void tracedMethodWithAttribute(@SpanAttribute("attributeName") String parameter) {
    }
}

```

#### Sample Traces

The traces below were exported using Zipkin.

##### Spring Web MVC - Server Span

```json
   {
      "traceId":"0371febbbfa76b2e285a08b53a055d17",
      "id":"9b782243ad7df179",
      "kind":"SERVER",
      "name":"webmvctracingfilter.dofilterinteral",
      "timestamp":1596841405866633,
      "duration":355648,
      "localEndpoint":{
         "serviceName":"sample_trace",
         "ipv4":"XXX.XXX.X.XXX"
      },
      "tags":{
         "http.client_ip":"0:0:0:0:0:0:0:1",
         "http.flavor":"1.1",
         "http.method":"GET",
         "http.status_code":"200",
         "http.url":"/spring-webmvc/sample",
         "http.user_agent":"PostmanRuntime/7.26.2",
         "net.sock.peer.addr":"0:0:0:0:0:0:0:1",
         "net.sock.peer.port":"33916",
         "net.sock.family":"inet6"
         "sampling.probability":"1.0"
      }
   }
```

##### Spring Web - RestTemplate Client Span

```json
{
  "traceId": "0371febbbfa76b2e285a08b53a055d17",
  "parentId": "9b782243ad7df179",
  "id": "43990118a8bdbdf5",
  "kind": "CLIENT",
  "name": "http get",
  "timestamp": 1596841405949825,
  "duration": 21288,
  "localEndpoint": {
    "serviceName": "sample_trace",
    "ipv4": "XXX.XXX.X.XXX"
  },
  "tags": {
    "http.method": "GET",
    "http.status_code": "200",
    "http.url": "/spring-web/sample/rest-template",
    "net.peer.name": "localhost",
    "net.peer.port": "8081"
  }
}
```

##### Spring Web-Flux - WebClient Span

```json
{
  "traceId": "0371febbbfa76b2e285a08b53a055d17",
  "parentId": "9b782243ad7df179",
  "id": "1b14a2fc89d7a762",
  "kind": "CLIENT",
  "name": "http post",
  "timestamp": 1596841406109125,
  "duration": 25137,
  "localEndpoint": {
    "serviceName": "sample_trace",
    "ipv4": "XXX.XXX.X.XXX"
  },
  "tags": {
    "http.method": "POST",
    "http.status_code": "200",
    "http.url": "/spring-webflux/sample/web-client",
    "net.peer.name": "localhost",
    "net.peer.port": "8082"
  }
}
```

##### @WithSpan Instrumentation

```
[
   {
      "traceId":"0371febbbfa76b2e285a08b53a055d17",
      "parentId":"9b782243ad7df179",
      "id":"c3ef24b9bff5901c",
      "name":"tracedclass.withspanmethod",
      "timestamp":1596841406165439,
      "duration":6912,
      "localEndpoint":{
         "serviceName":"sample_trace",
         "ipv4":"XXX.XXX.X.XXX"
      },
      "tags":{
         "test.type":"@WithSpan annotation",
         "test.case":'@WithSpan',
         "test.hasEvent":'true',
      }
   },
   {
      "traceId":"0371febbbfa76b2e285a08b53a055d17",
      "parentId":"9b782243ad7df179",
      "id":"1a6cb395a8a33cc0",
      "name":"@withspan set span name",
      "timestamp":1596841406182759,
      "duration":2187,
      "localEndpoint":{
         "serviceName":"sample_trace",
         "ipv4":"XXX.XXX.X.XXX"
      },
      "annotations":[
         {
            "timestamp":1596841406182920,
            "value":"ADD EVENT TO tracedMethodWithName SPAN"
         }
      ],
      "tags":{
         "test.type":"@WithSpan annotation",
         "test.case":'@WithSpan(value="@withspan set span name")',
         "test.hasEvent":'true',
      }
   },
   {
      "traceId":"0371febbbfa76b2e285a08b53a055d17",
      "parentId":"9b782243ad7df179",
      "id":"74dd19a8a9883f80",
      "kind":"CLIENT",
      "name":"tracedClientSpan",
      "timestamp":1596841406194210,
      "duration":130,
      "localEndpoint":{
         "serviceName":"sample_trace",
         "ipv4":"XXX.XXX.X.XXX"
      }
      "tags":{
         "test.type":"@WithSpan annotation",
         "test.case":"@WithSpan(kind=SpanKind.Client)",
      }
   },
]
```

#### Spring Support

Auto-configuration is natively supported by Springboot applications. To enable these features in "vanilla" use `@EnableOpenTelemetry` to complete a component scan of this package.

##### Usage

```java
import io.opentelemetry.instrumentation.spring.autoconfigure.EnableOpenTelemetry;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableOpenTelemetry
public class OpenTelemetryConfig {}
```

#### Exporter Configurations

This package provides auto configurations for [OTLP](https://github.com/open-telemetry/opentelemetry-java/tree/main/exporters/otlp), [Jaeger](https://github.com/open-telemetry/opentelemetry-java/tree/main/exporters/jaeger), [Zipkin](https://github.com/open-telemetry/opentelemetry-java/tree/main/exporters/zipkin), and [Logging](https://github.com/open-telemetry/opentelemetry-java/tree/main/exporters/logging) Span Exporters.

If an exporter is present in the classpath during runtime and a spring bean of the exporter is missing from the spring application context. An exporter bean is initialized and added to a simple span processor in the active tracer provider. Check out the implementation [here](./src/main/java/io/opentelemetry/instrumentation/spring/autoconfigure/OpenTelemetryAutoConfiguration.java).

#### Configuration Properties

##### Enabling/Disabling Features

| Feature          | Property                                    | Default Value | ConditionalOnClass     |
|------------------|---------------------------------------------|---------------|------------------------|
| spring-web       | otel.instrumentation.spring-webmvc.enabled  | `true`        | RestTemplate           |
| spring-webmvc    | otel.instrumentation.spring-web.enabled     | `true`        | OncePerRequestFilter   |
| spring-webflux   | otel.instrumentation.spring-webflux.enabled | `true`        | WebClient              |
| @WithSpan        | otel.instrumentation.annotations.enabled    | `true`        | WithSpan, Aspect       |
| Otlp Exporter    | otel.exporter.otlp.enabled                  | `true`        | OtlpGrpcSpanExporter   |
| Jaeger Exporter  | otel.exporter.jaeger.enabled                | `true`        | JaegerGrpcSpanExporter |
| Zipkin Exporter  | otel.exporter.zipkin.enabled                | `true`        | ZipkinSpanExporter     |
| Logging Exporter | otel.exporter.logging.enabled               | `true`        | LoggingSpanExporter    |

<!-- Slf4j Log Correlation  otel.springboot.loggers.slf4j.enabled		true   		org.slf4j.MDC -->

##### Resource Properties

| Feature  | Property                                         | Default Value          |
| -------- | ------------------------------------------------ | ---------------------- |
| Resource | otel.springboot.resource.enabled                 | `true`                 |
|          | otel.springboot.resource.attributes.service.name | `unknown_service:java` |
|          | otel.springboot.resource.attributes              | `empty map`            |

`unknown_service:java` will be used as the service-name if no value has been specified to the
property `spring.application.name` or `otel.springboot.resource.attributes.service.name` (which has
the highest priority)

`otel.springboot.resource.attributes` supports a pattern-based resource configuration in the
application.properties like this:

```
otel.springboot.resource.attributes.environment=dev
otel.springboot.resource.attributes.xyz=foo
```

##### Exporter Properties

| Feature         | Property                      | Default Value                        |
| --------------- | ----------------------------- | ------------------------------------ |
| Otlp Exporter   | otel.exporter.otlp.endpoint   | `localhost:4317`                     |
|                 | otel.exporter.otlp.timeout    | `1s`                                 |
| Jaeger Exporter | otel.exporter.jaeger.endpoint | `localhost:14250`                    |
|                 | otel.exporter.jaeger.timeout  | `1s`                                 |
| Zipkin Exporter | otel.exporter.jaeger.endpoint | `http://localhost:9411/api/v2/spans` |

##### Tracer Properties

| Feature | Property                        | Default Value |
| ------- | ------------------------------- | ------------- |
| Tracer  | otel.traces.sampler.probability | `1.0`         |

### Starter Guide

Check out [OpenTelemetry Manual Instrumentation](https://opentelemetry.io/docs/instrumentation/java/manual/) to learn more about
using the OpenTelemetry API to instrument your code.
