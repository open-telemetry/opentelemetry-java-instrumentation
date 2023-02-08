/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11;

import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanKind;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.kafka.internal.KafkaClientBaseTest;
import io.opentelemetry.instrumentation.kafka.internal.KafkaClientPropagationBaseTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.assertj.core.api.AbstractLongAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class KafkaClientSuppressReceiveSpansTest extends KafkaClientPropagationBaseTest {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void testKafkaProduceAndConsume() throws InterruptedException {
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
    @SuppressWarnings("PreferJavaTimeOverload")
    ConsumerRecords<?, ?> records = consumer.poll(Duration.ofSeconds(5).toMillis());
    for (ConsumerRecord<?, ?> record : records) {
      testing.runWithSpan(
          "processing",
          () -> {
            assertThat(record.value()).isEqualTo(greeting);
            assertThat(record.key()).isNull();
          });
    }

    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> {
                span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent();
              },
              span -> {
                span.hasName(SHARED_TOPIC + " send")
                    .hasKind(SpanKind.PRODUCER)
                    .hasParent(trace.getSpan(0))
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
              },
              span -> {
                span.hasName(SHARED_TOPIC + " process")
                    .hasKind(SpanKind.CONSUMER)
                    .hasParent(trace.getSpan(1))
                    .hasAttributesSatisfying(
                        equalTo(SemanticAttributes.MESSAGING_SYSTEM, "kafka"),
                        equalTo(SemanticAttributes.MESSAGING_DESTINATION, SHARED_TOPIC),
                        equalTo(SemanticAttributes.MESSAGING_DESTINATION_KIND, "topic"),
                        equalTo(SemanticAttributes.MESSAGING_OPERATION, "process"),
                        equalTo(
                            SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES,
                            greeting.getBytes(StandardCharsets.UTF_8).length),
                        satisfies(
                            SemanticAttributes.MESSAGING_KAFKA_PARTITION,
                            AbstractLongAssert::isNotNegative),
                        satisfies(
                            AttributeKey.longKey("messaging.kafka.message.offset"),
                            AbstractLongAssert::isNotNegative),
                        satisfies(
                            AttributeKey.longKey("kafka.record.queue_time_ms"),
                            AbstractLongAssert::isNotNegative));
              },
              span -> {
                span.hasName("processing").hasKind(SpanKind.INTERNAL).hasParent(trace.getSpan(2));
              },
              span -> {
                span.hasName("producer callback")
                    .hasKind(SpanKind.INTERNAL)
                    .hasParent(trace.getSpan(0));
              });
        });
  }

  @Test
  void testPassThroughTombstone()
      throws ExecutionException, InterruptedException, TimeoutException {
    producer.send(new ProducerRecord<>(SHARED_TOPIC, null)).get(5, TimeUnit.SECONDS);
    awaitUntilConsumerIsReady();
    @SuppressWarnings("PreferJavaTimeOverload")
    ConsumerRecords<?, ?> records = consumer.poll(Duration.ofSeconds(5).toMillis());
    assertThat(records.count()).isEqualTo(1);

    // iterate over records to generate spans
    for (ConsumerRecord<?, ?> record : records) {
      assertThat(record.value()).isNull();
      assertThat(record.key()).isNull();
    }

    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(SpanKind.INTERNAL, SpanKind.CONSUMER),
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> {
                span.hasName(SHARED_TOPIC + " send")
                    .hasKind(SpanKind.PRODUCER)
                    .hasNoParent()
                    .hasAttributesSatisfying(
                        equalTo(SemanticAttributes.MESSAGING_SYSTEM, "kafka"),
                        equalTo(SemanticAttributes.MESSAGING_DESTINATION, SHARED_TOPIC),
                        equalTo(SemanticAttributes.MESSAGING_KAFKA_TOMBSTONE, true),
                        equalTo(SemanticAttributes.MESSAGING_DESTINATION_KIND, "topic"),
                        satisfies(
                            SemanticAttributes.MESSAGING_KAFKA_PARTITION,
                            AbstractLongAssert::isNotNegative),
                        satisfies(
                            AttributeKey.longKey("messaging.kafka.message.offset"),
                            AbstractLongAssert::isNotNegative));
              },
              span -> {
                span.hasName(SHARED_TOPIC + " process")
                    .hasKind(SpanKind.CONSUMER)
                    .hasParent(trace.getSpan(0))
                    .hasAttributesSatisfying(
                        equalTo(SemanticAttributes.MESSAGING_SYSTEM, "kafka"),
                        equalTo(SemanticAttributes.MESSAGING_DESTINATION, SHARED_TOPIC),
                        equalTo(SemanticAttributes.MESSAGING_DESTINATION_KIND, "topic"),
                        equalTo(SemanticAttributes.MESSAGING_OPERATION, "process"),
                        equalTo(SemanticAttributes.MESSAGING_KAFKA_TOMBSTONE, true),
                        equalTo(SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES, -1L),
                        satisfies(
                            SemanticAttributes.MESSAGING_KAFKA_PARTITION,
                            AbstractLongAssert::isNotNegative),
                        satisfies(
                            AttributeKey.longKey("messaging.kafka.message.offset"),
                            AbstractLongAssert::isNotNegative),
                        satisfies(
                            AttributeKey.longKey("kafka.record.queue_time_ms"),
                            AbstractLongAssert::isNotNegative));
              });
        });
  }

  @Test
  void testRecordsWithTopicPartitionKafkaConsume()
      throws ExecutionException, InterruptedException, TimeoutException {
    String greeting = "Hello from MockConsumer!";
    producer
        .send(new ProducerRecord<>(SHARED_TOPIC, partition, null, greeting))
        .get(5, TimeUnit.SECONDS);

    testing.waitForTraces(1);

    awaitUntilConsumerIsReady();
    @SuppressWarnings("PreferJavaTimeOverload")
    ConsumerRecords<?, ?> consumerRecords = consumer.poll(Duration.ofSeconds(5).toMillis());
    List<? extends ConsumerRecord<?, ?>> recordsInPartition =
        consumerRecords.records(KafkaClientBaseTest.topicPartition);
    assertThat(recordsInPartition.size()).isEqualTo(1);

    // iterate over records to generate spans
    for (ConsumerRecord<?, ?> record : recordsInPartition) {
      assertThat(record.value()).isEqualTo(greeting);
      assertThat(record.key()).isNull();
    }

    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(SpanKind.INTERNAL, SpanKind.CONSUMER),
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
                        equalTo(SemanticAttributes.MESSAGING_KAFKA_PARTITION, partition),
                        satisfies(
                            AttributeKey.longKey("messaging.kafka.message.offset"),
                            AbstractLongAssert::isNotNegative));
              },
              span -> {
                span.hasName(SHARED_TOPIC + " process")
                    .hasKind(SpanKind.CONSUMER)
                    .hasParent(trace.getSpan(0))
                    .hasAttributesSatisfying(
                        equalTo(SemanticAttributes.MESSAGING_SYSTEM, "kafka"),
                        equalTo(SemanticAttributes.MESSAGING_DESTINATION, SHARED_TOPIC),
                        equalTo(SemanticAttributes.MESSAGING_DESTINATION_KIND, "topic"),
                        equalTo(SemanticAttributes.MESSAGING_OPERATION, "process"),
                        equalTo(
                            SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES,
                            greeting.getBytes(StandardCharsets.UTF_8).length),
                        equalTo(SemanticAttributes.MESSAGING_KAFKA_PARTITION, partition),
                        satisfies(
                            AttributeKey.longKey("messaging.kafka.message.offset"),
                            AbstractLongAssert::isNotNegative),
                        satisfies(
                            AttributeKey.longKey("kafka.record.queue_time_ms"),
                            AbstractLongAssert::isNotNegative));
              });
        });
  }
}
