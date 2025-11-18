# Library Instrumentation for Spring Integration version 4.1 and higher

Provides OpenTelemetry instrumentation for [Spring Integration](https://spring.io/projects/spring-integration),
enabling producer and consumer messaging spans for Spring Integration.

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-spring-integration-4.1).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-spring-integration-4.1</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```kotlin
implementation("io.opentelemetry.instrumentation:opentelemetry-spring-integration-4.1:OPENTELEMETRY_VERSION")
```

### Usage

The instrumentation library provides a `ChannelInterceptor` implementation that can be added
to your Spring Integration message channels to provide spans and context propagation.

```java
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.integration.v4_1.SpringIntegrationTelemetry;
import org.springframework.messaging.support.ChannelInterceptor;

public class SpringIntegrationConfiguration {

  // Use this ChannelInterceptor for intercepting Spring Integration message channels.
  public ChannelInterceptor createInterceptor(OpenTelemetry openTelemetry) {
    return SpringIntegrationTelemetry.builder(openTelemetry)
        .build()
        .newChannelInterceptor();
  }

  // Apply the interceptor to your message channels in your Spring configuration.
}
```
