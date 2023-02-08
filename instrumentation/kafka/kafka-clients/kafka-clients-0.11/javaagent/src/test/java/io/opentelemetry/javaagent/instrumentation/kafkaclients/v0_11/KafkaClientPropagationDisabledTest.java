/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.kafka.internal.KafkaClientPropagationBaseTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.assertj.core.api.AbstractLongAssert;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class KafkaClientPropagationDisabledTest extends KafkaClientPropagationBaseTest {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @DisplayName("should not read remote context when consuming messages if propagation is disabled")
  @Test
  void testReadRemoteContextWhenPropagationIsDisabled() throws InterruptedException {
    String message = "Testing without headers";
    producer.send(new ProducerRecord<>(SHARED_TOPIC, message));

    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> {
                span.hasName(SHARED_TOPIC + " send")
                    .hasKind(SpanKind.PRODUCER)
                    .hasNoParent()
                    .hasAttributesSatisfying(
                        equalTo(SemanticAttributes.MESSAGING_SYSTEM, "kafka"),
                        equalTo(SemanticAttributes.MESSAGING_DESTINATION, SHARED_TOPIC),
                        equalTo(SemanticAttributes.MESSAGING_DESTINATION_KIND, "topic"),
                        satisfies(
                            SemanticAttributes.MESSAGING_KAFKA_PARTITION,
                            AbstractLongAssert::isNotNegative),
                        satisfies(
                            AttributeKey.longKey("messaging.kafka.message.offset"),
                            AbstractLongAssert::isNotNegative));
              });
        });

    awaitUntilConsumerIsReady();

    @SuppressWarnings("PreferJavaTimeOverload")
    ConsumerRecords<?, ?> records = consumer.poll(Duration.ofSeconds(5).toMillis());
    assertThat(records.count()).isEqualTo(1);

    // iterate over records to generate spans
    for (ConsumerRecord<?, ?> ignored : records) {
      testing.runWithSpan("processing", () -> {});
    }

    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> {
                span.hasName(SHARED_TOPIC + " send")
                    .hasKind(SpanKind.PRODUCER)
                    .hasNoParent()
                    .hasAttributesSatisfying(
                        equalTo(SemanticAttributes.MESSAGING_SYSTEM, "kafka"),
                        equalTo(SemanticAttributes.MESSAGING_DESTINATION, SHARED_TOPIC),
                        equalTo(SemanticAttributes.MESSAGING_DESTINATION_KIND, "topic"),
                        satisfies(
                            SemanticAttributes.MESSAGING_KAFKA_PARTITION,
                            AbstractLongAssert::isNotNegative),
                        satisfies(
                            AttributeKey.longKey("messaging.kafka.message.offset"),
                            AbstractLongAssert::isNotNegative));
              });
        },
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> {
                span.hasName(SHARED_TOPIC + " process")
                    .hasKind(SpanKind.CONSUMER)
                    .hasLinks(Collections.emptyList())
                    .hasAttributesSatisfying(
                        equalTo(SemanticAttributes.MESSAGING_SYSTEM, "kafka"),
                        equalTo(SemanticAttributes.MESSAGING_DESTINATION, SHARED_TOPIC),
                        equalTo(SemanticAttributes.MESSAGING_DESTINATION_KIND, "topic"),
                        equalTo(SemanticAttributes.MESSAGING_OPERATION, "process"),
                        equalTo(
                            SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES,
                            message.getBytes(StandardCharsets.UTF_8).length),
                        equalTo(SemanticAttributes.MESSAGING_KAFKA_PARTITION, partition),
                        satisfies(
                            AttributeKey.longKey("messaging.kafka.message.offset"),
                            AbstractLongAssert::isNotNegative),
                        satisfies(
                            AttributeKey.longKey("kafka.record.queue_time_ms"),
                            AbstractLongAssert::isNotNegative));
              },
              span -> {
                span.hasName("processing").hasParent(trace.getSpan(0));
              });
        });
  }
}
