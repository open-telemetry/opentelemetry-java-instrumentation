# Library Instrumentation for Spring Kafka version 2.7 and higher

Provides OpenTelemetry instrumentation for [Spring Kafka](https://spring.io/projects/spring-kafka),
enabling consumer messaging spans for Spring Kafka listeners

## Quickstart

### Add these dependencies to your project

Replace `OPENTELEMETRY_VERSION` with the [latest release](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-spring-kafka-2.7).

For Maven, add to your `pom.xml` dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-spring-kafka-2.7</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

For Gradle, add to your dependencies:

```kotlin
implementation("io.opentelemetry.instrumentation:opentelemetry-spring-kafka-2.7:OPENTELEMETRY_VERSION")
```

### Usage

The instrumentation library provides interceptors that can be added to Spring Kafka message
listener containers to provide OpenTelemetry-based spans and context propagation.

```java
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.kafka.v2_7.SpringKafkaTelemetry;
import org.springframework.kafka.config.ContainerCustomizer;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;

public class SpringKafkaConfiguration {

  // Use this ContainerCustomizer to add interceptors to your Kafka listener containers.
  public ContainerCustomizer<String, String, ConcurrentMessageListenerContainer<String, String>>
      createListenerCustomizer(OpenTelemetry openTelemetry) {
    SpringKafkaTelemetry telemetry = SpringKafkaTelemetry.builder(openTelemetry).build();
    return container -> {
      container.setRecordInterceptor(telemetry.createRecordInterceptor());
      container.setBatchInterceptor(telemetry.createBatchInterceptor());
    };
  }

  // Configure the customizer in your Spring Kafka configuration.
}
```
