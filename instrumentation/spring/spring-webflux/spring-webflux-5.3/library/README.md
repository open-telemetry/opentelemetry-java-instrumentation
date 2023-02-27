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

### WebClient Instrumentation

`SpringWebfluxTelemetry` emits a client span for each request sent using `WebClient` by implementing
the [ExchangeFilterFunction](https://docs.spring.io/spring/docs/current/javadoc-api/org/springframework/web/reactive/function/client/ExchangeFilterFunction.html)
interface. An example is shown below:

##### Usage

```java
import io.opentelemetry.instrumentation.spring.webflux.v5_3.client.SpringWebfluxTelemetry;

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

### Webflux Server Instrumentation

`SpringWebfluxServerTelemetry` emits a server span for each request received, by implementing
a `WebFilter` and using the OpenTelemetry Reactor instrumentation to ensure context is
passed around correctly.

#### Usage

```java
import io.opentelemetry.instrumentation.spring.webflux.v5_3.server.SpringWebfluxServerTelemetry;

@Configuration
public class WebFilterConfig {

  @Bean
  public WebFilter webFilter(OpenTelemetry openTelemetry) {
    return SpringWebfluxServerTelemetry.builder(openTelemetry)
        .build()
        .createWebFilterAndRegisterReactorHook();
  }
}
```

## Starter Guide

Check
out [OpenTelemetry Manual Instrumentation](https://opentelemetry.io/docs/instrumentation/java/manual/)
to learn more about using the OpenTelemetry API to instrument your code.
