# Library Instrumentation for Spring Cloud Gateway

Records the matched Spring Cloud Gateway route ID on WebFlux server spans and metrics when WebFlux
does not provide a route template. This library requires Spring WebFlux 5.3 or later. The oldest
combination covered by its integration tests is Spring Cloud Gateway 3.1.8 on Spring Boot 2.7.18.
The Java agent supports Spring Cloud Gateway 2.0 and later.

## Quickstart

Replace `OPENTELEMETRY_VERSION` with the
[latest release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-spring-cloud-gateway-2.0).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependency>
  <groupId>io.opentelemetry.instrumentation</groupId>
  <artifactId>opentelemetry-spring-cloud-gateway-2.0</artifactId>
  <version>OPENTELEMETRY_VERSION</version>
</dependency>
```

For Gradle, add to your dependencies:

```kotlin
implementation("io.opentelemetry.instrumentation:opentelemetry-spring-cloud-gateway-2.0:OPENTELEMETRY_VERSION")
```

The OpenTelemetry Spring Boot starter configures this automatically. For standalone use, also add
the [OpenTelemetry WebFlux library](../../../spring-webflux/spring-webflux-5.3/library) and set the
route fallback on its server telemetry builder:

```java
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.cloud.gateway.v2_0.SpringCloudGatewayRouteGetter;
import io.opentelemetry.instrumentation.spring.webflux.v5_3.SpringWebfluxServerTelemetry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.WebFilter;

@Configuration
class OpenTelemetryGatewayConfiguration {

  @Bean
  WebFilter openTelemetryWebFluxFilter(OpenTelemetry openTelemetry) {
    return SpringWebfluxServerTelemetry.builder(openTelemetry)
        .setRouteFallback(new SpringCloudGatewayRouteGetter())
        .build()
        .createWebFilterAndRegisterReactorHook();
  }
}
```
