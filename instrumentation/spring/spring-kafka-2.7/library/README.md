# Library Instrumentation for Spring Kafka version 2.7 and higher

Provides OpenTelemetry instrumentation for [Spring Kafka](https://spring.io/projects/spring-kafka),
enabling consumer and producer messaging spans.

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
listener containers and producers to provide spans and context propagation.

```java
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.kafkaclients.v2_6.KafkaTelemetry;
import io.opentelemetry.instrumentation.spring.kafka.v2_7.SpringKafkaTelemetry;
import org.springframework.boot.autoconfigure.kafka.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ContainerCustomizer;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;

@Configuration
public class KafkaInstrumentationConfig {

  // Instrument Kafka producers
  @Bean
  public DefaultKafkaProducerFactoryCustomizer producerInstrumentation(
      OpenTelemetry openTelemetry) {
    KafkaTelemetry kafkaTelemetry = KafkaTelemetry.create(openTelemetry);
    return producerFactory -> producerFactory.addPostProcessor(kafkaTelemetry::wrap);
  }

  // Instrument Kafka consumers
  @Bean
  public ContainerCustomizer<String, String, ConcurrentMessageListenerContainer<String, String>>
      listenerCustomizer(OpenTelemetry openTelemetry) {
    SpringKafkaTelemetry springKafkaTelemetry = SpringKafkaTelemetry.create(openTelemetry);
    return container -> {
      container.setRecordInterceptor(springKafkaTelemetry.createRecordInterceptor());
      container.setBatchInterceptor(springKafkaTelemetry.createBatchInterceptor());
    };
  }
}
```
