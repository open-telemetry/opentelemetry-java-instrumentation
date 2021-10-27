# Manual Instrumentation for Spring Webflux

Provides OpenTelemetry instrumentation for Spring's `WebClient`.

## Quickstart

### Add these dependencies to your project.

Replace `SPRING_VERSION` with the version of spring you're using.
`Minimum version: 5.0`

Replace `OPENTELEMETRY_VERSION` with the latest stable [release](https://mvnrepository.com/artifact/io.opentelemetry).
`Minimum version: 1.8.0`

For Maven add to your `pom.xml`:

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
    <artifactId>opentelemetry-exporters-logging</artifactId>
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

For Gradle add to your dependencies:

```groovy
// opentelemetry instrumentation
implementation("io.opentelemetry.instrumentation:opentelemetry-spring-webflux-5.0:OPENTELEMETRY_VERSION")

// opentelemetry exporter
// replace this default exporter with your opentelemetry exporter (ex. otlp/zipkin/jaeger/..)
implementation("io.opentelemetry:opentelemetry-exporters-logging:OPENTELEMETRY_VERSION")

// required to instrument spring-webmvc
// this artifact should already be present in your application
implementation("org.springframework:spring-webflux:SPRING_VERSION")
```

### Features

#### `SpringWebfluxTracing`

`SpringWebfluxTracing` emits client span for each request sent using `WebClient` by implementing
the [ExchangeFilterFunction](https://docs.spring.io/spring/docs/current/javadoc-api/org/springframework/web/reactive/function/client/ExchangeFilterFunction.html)
interface. An example is shown below:

##### Usage

```java

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.webflux.client.SpringWebfluxTracing;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

   @Bean
   public WebClient.Builder webClient(OpenTelemetry openTelemetry) {

      WebClient webClient = WebClient.create();
      SpringWebfluxTracing instrumentation = SpringWebfluxTracing.create(openTelemetry);

      return webClient.mutate().filter(instrumentation::addClientTracingFilter);
   }
}
```

### Starter Guide

Check out the opentelemetry [quick start](https://github.com/open-telemetry/opentelemetry-java/blob/master/QUICKSTART.md) to learn more about OpenTelemetry instrumentation.
