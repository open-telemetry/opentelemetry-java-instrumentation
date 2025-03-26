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
    return webClient.mutate().filters(webfluxClientTelemetry::addFilter);
  }

  // Adds instrumentation to Webflux server
  @Bean
  public WebFilter webFilter() {
    return webfluxServerTelemetry.createWebFilterAndRegisterReactorHook();
  }
}
```

## Starter Guide

Check
out [OpenTelemetry Manual Instrumentation](https://opentelemetry.io/docs/instrumentation/java/manual/)
to learn more about using the OpenTelemetry API to instrument your code.
