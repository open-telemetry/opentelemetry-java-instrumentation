# Manual Instrumentation for Spring Web MVC

Provides OpenTelemetry tracing for spring-webmvc RestControllers by leveraging spring-webmvc [filters](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/filter).

## Quickstart

### Add these dependencies to your project.

Replace `SPRING_VERSION` with the version of spring you're using.
 - `Minimum version: 3.1`

Replace `OPENTELEMETRY_VERSION` with the latest stable [release](https://mvnrepository.com/artifact/io.opentelemetry).
 - `Minimum version: 1.7.0`

For Maven add to your `pom.xml`:

```xml
<dependencies>
  <!-- opentelemetry instrumentation -->
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-spring-webmvc-3.1</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>

   <!-- opentelemetry exporter -->
   <!-- replace this default exporter with your opentelemetry exporter (ex. otlp/zipkin/jaeger/..) -->
   <dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporters-logging</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>

  <!-- required to instrument spring-webmvc -->
  <!-- this artifact should already be present in your application -->
  <dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-webmvc</artifactId>
    <version>SPRING_VERSION</version>
  </dependency>

</dependencies>
```

For Gradle add to your dependencies:

```groovy

// opentelemetry instrumentation
implementation("io.opentelemetry.instrumentation:opentelemetry-spring-webmvc-3.1:OPENTELEMETRY_VERSION")

// opentelemetry exporter
// replace this default exporter with your opentelemetry exporter (ex. otlp/zipkin/jaeger/..)
implementation("io.opentelemetry:opentelemetry-exporters-logging:OPENTELEMETRY_VERSION")

// required to instrument spring-webmvc
// this artifact should already be present in your application
implementation("org.springframework:spring-webmvc:SPRING_VERSION")
```

### Features

#### SpringWebMvcTracing

`SpringWebMvcTracing` adds OpenTelemetry server spans to requests processed by request dispatch, on any spring servlet container. An example is shown below:

##### Usage

```java
import io.opentelemetry.instrumentation.spring.webmvc.SpringWebMvcTracing;
import io.opentelemetry.api.trace.Tracer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class WebMvcTracingFilterConfig {

   @Bean
   public WebMvcTracingFilter webMvcTracingFilter(OpenTelemetry openTelemetry) {
      return SpringWebMvcTracing.create(openTelemetry).newServletFilter();
   }
}
```

### Starter Guide

Check out the OpenTelemetry [quick start](https://github.com/open-telemetry/opentelemetry-java/blob/master/QUICKSTART.md) to learn more about OpenTelemetry instrumentation.
