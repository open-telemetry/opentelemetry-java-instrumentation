/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.v2_6;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.kafka.internal.KafkaClientBaseTest;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.semconv.SemanticAttributes;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.assertj.core.api.AbstractLongAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class InterceptorsTest extends KafkaClientBaseTest {
  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Override
  public Map<String, Object> producerProps() {
    Map<String, Object> props = super.producerProps();
    props.put(
        ProducerConfig.INTERCEPTOR_CLASSES_CONFIG, TracingProducerInterceptor.class.getName());
    return props;
  }

  @Override
  public Map<String, Object> consumerProps() {
    Map<String, Object> props = super.consumerProps();
    props.put(
        ConsumerConfig.INTERCEPTOR_CLASSES_CONFIG, TracingConsumerInterceptor.class.getName());
    return props;
  }

  @Test
  void testInterceptors() throws InterruptedException {
    String greeting = "Hello Kafka!";
    testing.runWithSpan(
        "parent",
        () -> {
          producer.send(
              new ProducerRecord<>(SHARED_TOPIC, greeting),
              (meta, ex) -> {
                if (ex == null) {
                  testing.runWithSpan("producer callback", () -> {});
                } else {
                  testing.runWithSpan("producer exception: " + ex, () -> {});
                }
              });
        });

    awaitUntilConsumerIsReady();
    // check that the message was received
    ConsumerRecords<?, ?> records = consumer.poll(Duration.ofSeconds(5));
    assertThat(records.count()).isEqualTo(1);
    for (ConsumerRecord<?, ?> record : records) {
      assertThat(record.value()).isEqualTo(greeting);
      assertThat(record.key()).isNull();
    }

    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> {
                span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent();
              },
              span -> {
                span.hasName(SHARED_TOPIC + " publish")
                    .hasKind(SpanKind.PRODUCER)
                    .hasParent(trace.getSpan(0))
                    .hasAttributesSatisfyingExactly(
                        equalTo(SemanticAttributes.MESSAGING_SYSTEM, "kafka"),
                        equalTo(SemanticAttributes.MESSAGING_DESTINATION_NAME, SHARED_TOPIC),
                        satisfies(
                            SemanticAttributes.MESSAGING_CLIENT_ID,
                            stringAssert -> stringAssert.startsWith("producer")));
              },
              span -> {
                span.hasName(SHARED_TOPIC + " receive")
                    .hasKind(SpanKind.CONSUMER)
                    .hasParent(trace.getSpan(1))
                    .hasAttributesSatisfyingExactly(
                        equalTo(SemanticAttributes.MESSAGING_SYSTEM, "kafka"),
                        equalTo(SemanticAttributes.MESSAGING_DESTINATION_NAME, SHARED_TOPIC),
                        equalTo(SemanticAttributes.MESSAGING_OPERATION, "receive"),
                        equalTo(
                            SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES,
                            greeting.getBytes(StandardCharsets.UTF_8).length),
                        satisfies(
                            SemanticAttributes.MESSAGING_KAFKA_DESTINATION_PARTITION,
                            AbstractLongAssert::isNotNegative),
                        satisfies(
                            SemanticAttributes.MESSAGING_KAFKA_MESSAGE_OFFSET,
                            AbstractLongAssert::isNotNegative),
                        equalTo(SemanticAttributes.MESSAGING_KAFKA_CONSUMER_GROUP, "test"),
                        satisfies(
                            SemanticAttributes.MESSAGING_CLIENT_ID,
                            stringAssert -> stringAssert.startsWith("consumer")));
              });
        },
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> {
                span.hasName("producer callback").hasKind(SpanKind.INTERNAL).hasNoParent();
              });
        });
  }
}
