# Library Instrumentation for Spring Webflux

Provides OpenTelemetry instrumentation for Spring's `WebClient` and Webflux server.

For this instrumentation, the minimum supported version of Spring Webflux is 5.3.0.

## Add dependencies to your project

For Maven, add to your `pom.xml`:

```xml

<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-spring-webflux-5.3</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>

  <!-- This artifact should already be present in your application -->
  <dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-webflux</artifactId>
    <version>SPRING_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```groovy
implementation("io.opentelemetry.instrumentation:opentelemetry-spring-webflux-5.3:OPENTELEMETRY_VERSION")

// this artifact should already be present in your application
implementation("org.springframework:spring-webflux:SPRING_VERSION")
```

## Features

`SpringWebfluxTelemetry` can emit a client span for each request sent using `WebClient` by
implementing
the [ExchangeFilterFunction](https://docs.spring.io/spring/docs/current/javadoc-api/org/springframework/web/reactive/function/client/ExchangeFilterFunction.html)
interface.

`SpringWebfluxTelemetry` can also emit a server span for each request received, by implementing
a `WebFilter` and using the OpenTelemetry Reactor instrumentation to ensure context is
passed around correctly.

### Web client instrumentation

The `WebClient` instrumentation will emit the `error.type` attribute with value `cancelled` whenever
an outgoing HTTP request is cancelled.

Experimental HTTP client telemetry captures a URI template only when it contains at least one URI
variable and an identifiable absolute-path reference. Scheme and authority are excluded; literal
path, query, and fragment text is trusted and preserved without sanitization in the span name,
`url.template`, and HTTP client duration metric attributes. Pass sensitive or per-request values as
URI variables rather than embedding them in template literals, and keep templates low-cardinality.

Enable experimental client telemetry programmatically:

```java
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.webflux.v5_3.SpringWebfluxClientTelemetry;
import io.opentelemetry.instrumentation.spring.webflux.v5_3.SpringWebfluxClientTelemetryBuilder;
import io.opentelemetry.instrumentation.spring.webflux.v5_3.internal.Experimental;

OpenTelemetry openTelemetry = ...;
SpringWebfluxClientTelemetryBuilder builder = SpringWebfluxClientTelemetry.builder(openTelemetry);
Experimental.setEmitExperimentalTelemetry(builder, true);
SpringWebfluxClientTelemetry telemetry = builder.build();
```

### Setup

Here is how to set up client and server instrumentation respectively:

```java
import io.opentelemetry.instrumentation.spring.webflux.v5_3.SpringWebfluxClientTelemetry;
import io.opentelemetry.instrumentation.spring.webflux.v5_3.SpringWebfluxServerTelemetry;

@Configuration
public class WebClientConfig {
  private final SpringWebfluxClientTelemetry webfluxClientTelemetry;
  private final SpringWebfluxServerTelemetry webfluxServerTelemetry;

  public WebClientConfig(OpenTelemetry openTelemetry) {
    this.webfluxClientTelemetry = SpringWebfluxClientTelemetry.builder(openTelemetry).build();
  }

  // Adds instrumentation to WebClients
  @Bean
  public WebClient.Builder webClient() {
    WebClient webClient = WebClient.create();
    return webClient.mutate().filter(webfluxClientTelemetry.createFilter());
  }

  // Adds instrumentation to Webflux server
  @Bean
  public WebFilter webFilter() {
    return webfluxServerTelemetry.createWebFilterAndRegisterReactorHook();
  }
}
```

### Reactor context propagation hook

Spring WebFlux runs on Project Reactor, which can switch threads across a reactive pipeline.
OpenTelemetry context is stored in a `ThreadLocal` and will not survive these switches without the Reactor context propagation hook.

**When you need the hook:**
Use `createWebFilterAndRegisterReactorHook()` or `createFilterAndRegisterReactorHook()` when application code or downstream instrumentation needs the current OpenTelemetry context to be available inside Reactor callbacks and the OpenTelemetry Reactor hook has not already been registered.
Otherwise, use `createWebFilter()` or `createFilter()`.

## Starter Guide

Check
out [OpenTelemetry Manual Instrumentation](https://opentelemetry.io/docs/instrumentation/java/manual/)
to learn more about using the OpenTelemetry API to instrument your code.
