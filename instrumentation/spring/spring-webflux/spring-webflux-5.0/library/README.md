# Library Instrumentation for Spring Webflux version 5.0 and higher

Provides OpenTelemetry instrumentation for Spring's `WebClient`.

## Quickstart

### Add these dependencies to your project

Replace `SPRING_VERSION` with the version of spring you're using.
`Minimum version: 5.0`

Replace `OPENTELEMETRY_VERSION` with the [latest
release](https://search.maven.org/search?q=g:io.opentelemetry.instrumentation%20AND%20a:opentelemetry-spring-webflux-5.0).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <!-- opentelemetry instrumentation -->
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-spring-webflux-5.0</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>

   <!-- opentelemetry exporter -->
   <!-- replace this default exporter with your opentelemetry exporter (ex. otlp/zipkin/jaeger/..) -->
   <dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-logging</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>

  <!-- required to instrument spring-webflux -->
  <!-- this artifact should already be present in your application -->
  <dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-webflux</artifactId>
    <version>SPRING_VERSION</version>
  </dependency>

</dependencies>
```

For Gradle, add to your dependencies:

```groovy
// opentelemetry instrumentation
implementation("io.opentelemetry.instrumentation:opentelemetry-spring-webflux-5.0:OPENTELEMETRY_VERSION")

// opentelemetry exporter
// replace this default exporter with your opentelemetry exporter (ex. otlp/zipkin/jaeger/..)
implementation("io.opentelemetry:opentelemetry-exporter-logging:OPENTELEMETRY_VERSION")

// required to instrument spring-webmvc
// this artifact should already be present in your application
implementation("org.springframework:spring-webflux:SPRING_VERSION")
```

### Features

#### `WebClient` instrumentation

`SpringWebfluxTelemetry` emits client span for each request sent using `WebClient` by
implementing
the [ExchangeFilterFunction](https://docs.spring.io/spring/docs/current/javadoc-api/org/springframework/web/reactive/function/client/ExchangeFilterFunction.html)
interface. An example is shown below:

##### Usage

```java
import io.opentelemetry.instrumentation.spring.webflux.v5_0.client.SpringWebfluxTelemetry;

@Configuration
public class WebClientConfig {

  @Bean
  public WebClient.Builder webClient(OpenTelemetry openTelemetry) {

    WebClient webClient = WebClient.create();
    SpringWebfluxTelemetry instrumentation = SpringWebfluxTelemetry.create(openTelemetry);

    return webClient.mutate().filters(instrumentation::addClientTracingFilter);
  }
}
```

#### Webflux server instrumentation

`SpringWebfluxServerTelemetry` emits a server span for each request received, by implementing
a `WebFilter` and using the OpenTelemetry Reactor instrumentation to ensure context is
passed around correctly.

#### Usage

```java
import io.opentelemetry.instrumentation.spring.webflux.v5_0.server.SpringWebfluxServerTelemetry;

@Configuration
public class WebFilterConfig {

  @Bean
  public WebFilter webFilter(OpenTelemetry openTelemetry) {
    return SpringWebfluxServerTelemetry.builder(openTelemetry).build().createWebFilterAndRegisterReactorHook();
  }
}
```

### Starter Guide

Check
out [OpenTelemetry Manual Instrumentation](https://opentelemetry.io/docs/instrumentation/java/manual/)
to learn more about
using the OpenTelemetry API to instrument your code.
