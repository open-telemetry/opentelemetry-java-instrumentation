# Manual Instrumentation for Spring-WebMvc

Provides OpenTelemetry tracing for spring-webmvc RestControllers by leveraging spring-webmvc [filters](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/filter).

## Quickstart

### Add these dependencies to your project.

Replace `SPRING_VERSION` with the version of spring you're using.
`Minimum version: 3.1`

Replace `OPENTELEMETRY_VERSION` with the latest stable [release](https://mvnrepository.com/artifact/io.opentelemetry).
`Minimum version: 0.8.0`

For Maven add to your `pom.xml`:
```xml
<dependencies>
  <!-- opentelemetry -->
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-spring-webmvc-3.1</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>

   <!-- provides opentelemetry-sdk -->
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
implementation 'io.opentelemetry.instrumentation:opentelemetry-spring-webmvc-3.1:OPENTELEMETRY_VERSION'
implementation 'io.opentelemetry:opentelemetry-exporters-logging:OPENTELEMETRY_VERSION'

//this artifact should already be present in your application
implementation 'org.springframework:spring-webmvc:SPRING_VERSION'
```

### Features

#### WebMvcTracingFilter

WebMvcTracingFilter adds OpenTelemetry server spans to requests processed by request dispatch, on any spring servlet container. An example is shown below:

##### Usage

```java

import io.opentelemetry.instrumentation.spring.webmvc.WebMvcTracingFilter
import io.opentelemetry.trace.Tracer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class WebMvcTracingFilterConfig {

   @Bean
   public WebMvcTracingFilter webMvcTracingFilter(Tracer tracer) {
      return new WebMvcTracingFilter(tracer);
   }
}
```

### Starter Guide

Check out the opentelemetry [quick start](https://github.com/open-telemetry/opentelemetry-java/blob/master/QUICKSTART.md) to learn more about OpenTelemetry instrumentation.
