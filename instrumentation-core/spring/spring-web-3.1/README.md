# Manual Instrumentation for Spring-Web

Provides OpenTelemetry instrumentation for Spring's RestTemplate.

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
    <artifactId>opentelemetry-spring-web-3.1</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>

   <!-- provides opentelemetry-sdk -->
   <dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporters-logging</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>

  <!-- required to instrument spring-web -->
  <!-- this artifact should already be present in your application -->
  <dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-web</artifactId>
    <version>SPRING_VERSION</version>
  </dependency>

</dependencies>
```

For Gradle add to your dependencies:
```groovy
implementation 'io.opentelemetry.instrumentation:opentelemetry-spring-web-3.1:OPENTELEMETRY_VERSION'
implementation 'io.opentelemetry:opentelemetry-exporters-logging:OPENTELEMETRY_VERSION'

//this artifact should already be present in your application
runtime 'org.springframework:spring-web:SPRING_VERSION'
```

### Features

#### RestTemplateInterceptor

RestTemplateInterceptor adds OpenTelemetry client spans to requests sent using RestTemplate by implementing the [ClientHttpRequestInterceptor](https://docs.spring.io/spring/docs/current/javadoc-api/org/springframework/http/client/ClientHttpRequestInterceptor.html)
interface. An example is shown below:

##### Usage

```java

import io.opentelemetry.instrumentation.spring.httpclients.RestTemplateInterceptor
import io.opentelemetry.trace.Tracer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

   @Bean
   public RestTemplate restTemplate(Tracer tracer) {

      RestTemplate restTemplate = new RestTemplate();
      RestTemplateInterceptor restTemplateInterceptor = new RestTemplateInterceptor(tracer);
      restTemplate.getInterceptors().add(restTemplateInterceptor);

      return restTemplate;
   }
}
```

### Starter Guide

Check out the opentelemetry [quick start](https://github.com/open-telemetry/opentelemetry-java/blob/master/QUICKSTART.md) to learn more about OpenTelemetry instrumentation.
