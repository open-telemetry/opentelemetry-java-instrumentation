# Library Instrumentation for Spring Webflux

Provides OpenTelemetry instrumentation for Spring's `WebClient` and Webflux server.

For the `WebClient` instrumentation, the minimum supported version of Spring Webflux is 5.0, and
so this instrumentation can be found in the `opentelemetry-spring-webflux-5.0` module.
For the Webflux server instrumentation, the minimum supported version is 5.3, and so it can be
found it the `opentelemetry-spring-webflux-5.3` module.
You may use one or both of these packages at the same time as you wish.

## Add dependencies to your project

Replace `SPRING_VERSION` with the version of spring you're using
(Minimum 5.3, or 5.0 if only using client instrumentation).

Replace `OPENTELEMETRY_VERSION` with the [latest
release](https://search.maven.org/search?q=g:io.opentelemetry.instrumentation%20AND%20a:opentelemetry-spring-webflux-5.0)
.

For Maven, add to your `pom.xml`:

```xml

<dependencies>
  <!-- OpenTelemetry WebClient instrumentation -->
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-spring-webflux-5.0</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>

  <!-- OpenTelemetry Webflux server instrumentation -->
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
// OpenTelemetry WebClient instrumentation
implementation("io.opentelemetry.instrumentation:opentelemetry-spring-webflux-5.0:OPENTELEMETRY_VERSION")

// OpenTelemetry Webflux server instrumentation
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
