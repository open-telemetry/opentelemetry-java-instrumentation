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

For Spring Boot 2.x and 3.x, instrument producers with:

```java
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.kafkaclients.v2_6.KafkaTelemetry;
import org.springframework.boot.autoconfigure.kafka.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaInstrumentationConfig {

  @Bean
  public DefaultKafkaProducerFactoryCustomizer producerInstrumentation(
      OpenTelemetry openTelemetry) {
    KafkaTelemetry kafkaTelemetry = KafkaTelemetry.create(openTelemetry);
    return producerFactory -> producerFactory.addPostProcessor(kafkaTelemetry::instrument);
  }
}
```

For Spring Boot 4.x, use the new package for the same customizer:

```java
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.kafkaclients.v2_6.KafkaTelemetry;
import org.springframework.boot.kafka.autoconfigure.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaInstrumentationConfig {

  @Bean
  public DefaultKafkaProducerFactoryCustomizer producerInstrumentation(
      OpenTelemetry openTelemetry) {
    KafkaTelemetry kafkaTelemetry = KafkaTelemetry.create(openTelemetry);
    return producerFactory -> producerFactory.addPostProcessor(kafkaTelemetry::instrument);
  }
}
```

Instrument consumers with:

```java
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.kafka.v2_7.SpringKafkaTelemetry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ContainerCustomizer;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;

@Configuration
public class KafkaInstrumentationConfig {

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

With Spring Kafka 2.7, the container does not notify the batch interceptor when a batch does not
complete, for example when `interceptBeforeTx` is enabled and the transaction can't be started, or
when the listener throws an `Error`. The batch span then stays open and its context stays current
on the consumer thread. To avoid this, instrument batch listeners by wrapping the listener instead
of setting the batch interceptor:

```java
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.kafka.v2_7.SpringKafkaTelemetry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ContainerCustomizer;
import org.springframework.kafka.listener.BatchMessageListener;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;

@Configuration
public class KafkaInstrumentationConfig {

  @Bean
  public ContainerCustomizer<String, String, ConcurrentMessageListenerContainer<String, String>>
      listenerCustomizer(OpenTelemetry openTelemetry) {
    SpringKafkaTelemetry springKafkaTelemetry = SpringKafkaTelemetry.create(openTelemetry);
    return container -> {
      container.setRecordInterceptor(springKafkaTelemetry.createRecordInterceptor());
      Object listener = container.getContainerProperties().getMessageListener();
      if (listener instanceof BatchMessageListener) {
        container.setupMessageListener(
            springKafkaTelemetry.createBatchMessageListener(
                (BatchMessageListener<String, String>) listener));
      }
    };
  }
}
```

When consumers are also wrapped with `KafkaTelemetry.instrument(...)`, wrapping the batch listener
alone does not prevent Kafka process spans during Spring's pre-listener batch conversion. Add the
Spring wrapper after the Kafka wrapper to the `DefaultKafkaConsumerFactory` used by the container:

```java
consumerFactory.addPostProcessor(
    consumer -> springKafkaTelemetry.instrument(kafkaTelemetry.instrument(consumer)));
```

The wrapped listener's span covers the listener invocation, so batches that never reach the
listener are not traced. Don't combine it with the batch interceptor on the same container, because
each creates a span for the batch. Spring Kafka 2.8 and later notify the batch interceptor after
every poll, so the batch interceptor doesn't have this limitation there.
