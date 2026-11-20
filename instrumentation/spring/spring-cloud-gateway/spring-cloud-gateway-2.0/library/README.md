# Library Instrumentation for Spring Cloud Gateway 2.0 and higher

Provides a Spring Cloud Gateway `GlobalFilter` that records the matched route ID as `http.route`
on existing HTTP server spans and metrics.

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

Then register the route filter as a Spring bean:

```java
import io.opentelemetry.instrumentation.spring.cloud.gateway.v2_0.SpringCloudGatewayRouteFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenTelemetryGatewayConfiguration {

  @Bean
  SpringCloudGatewayRouteFilter openTelemetrySpringCloudGatewayRouteFilter() {
    return new SpringCloudGatewayRouteFilter();
  }
}
```

The filter enriches telemetry produced by HTTP server instrumentation and does not create spans or
metrics itself. It requires the WebFlux Reactor context hook provided by the OpenTelemetry Spring
Boot starter or by registering the WebFlux library instrumentation with
`createWebFilterAndRegisterReactorHook()`. When using the starter, the route filter is registered
automatically unless the application already provides one.
