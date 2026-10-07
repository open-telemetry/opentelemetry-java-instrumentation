/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11;

import static io.opentelemetry.instrumentation.testing.junit.messaging.KafkaMessagingMetricsAssertions.assertProcessMetricsWithConsumedMessages;
import static io.opentelemetry.instrumentation.testing.junit.messaging.KafkaMessagingMetricsAssertions.assertSendMetrics;
import static io.opentelemetry.instrumentation.testing.junit.messaging.KafkaMessagingMetricsAssertions.assertTotalConsumedMessages;
import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaClientBaseTest;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaClientPropagationBaseTest;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaConsumerContextUtil;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
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
          ProducerRecord<Integer, String> producerRecord =
              new ProducerRecord<>(SHARED_TOPIC, 10, greeting);
          producerRecord
              .headers()
              // adding baggage header in w3c baggage format
              .add("baggage", "test-baggage-key-1=test-baggage-value-1".getBytes(UTF_8))
              .add("baggage", "test-baggage-key-2=test-baggage-value-2".getBytes(UTF_8));
          producer.send(
              producerRecord,
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
    ConsumerRecords<?, ?> records;
    Context inheritedContext =
        KafkaConsumerContextUtil.withReceiveOperation(Context.current(), true);
    try (Scope ignored = inheritedContext.makeCurrent()) {
      records = poll(Duration.ofSeconds(5));
    }
    for (ConsumerRecord<?, ?> record : records) {
      testing.runWithSpan(
          "processing",
          () -> {
            assertThat(record.key()).isEqualTo(10);
            assertThat(record.value()).isEqualTo(greeting);
          });
    }

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName(spanName("send"))
                        .hasKind(SpanKind.PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(sendAttributes("10", greeting, false)),
                span ->
                    span.hasName(spanName("process"))
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            processAttributes("10", greeting, false, true)),
                span ->
                    span.hasName("processing")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(2)),
                span ->
                    span.hasName("producer callback")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
    String instrumentationName = "io.opentelemetry.kafka-clients-0.11";
    assertSendMetrics(testing, instrumentationName, SHARED_TOPIC, "0", 1, null);
    assertProcessMetricsWithConsumedMessages(
        testing,
        instrumentationName,
        SHARED_TOPIC,
        testLatestDeps() ? "test" : null,
        "0",
        1,
        1,
        null);
    assertTotalConsumedMessages(testing, instrumentationName, 1);
  }

  @Test
  void testPassThroughTombstone() throws Exception {
    producer.send(new ProducerRecord<>(SHARED_TOPIC, null)).get(5, SECONDS);
    awaitUntilConsumerIsReady();
    ConsumerRecords<?, ?> records = poll(Duration.ofSeconds(5));
    assertThat(records.count()).isEqualTo(1);

    // iterate over records to generate spans
    for (ConsumerRecord<?, ?> record : records) {
      assertThat(record.value()).isNull();
      assertThat(record.key()).isNull();
    }

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(spanName("send"))
                        .hasKind(SpanKind.PRODUCER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(sendAttributes(null, null, false)),
                span ->
                    span.hasName(spanName("process"))
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            processAttributes(null, null, false, false))));
  }

  @Test
  void testAbandonedIteratorDoesNotSuppressNextPoll() throws Exception {
    producer.send(new ProducerRecord<>(SHARED_TOPIC, "first")).get(5, SECONDS);
    awaitUntilConsumerIsReady();
    ConsumerRecords<?, ?> firstRecords = poll(Duration.ofSeconds(5));
    Iterator<? extends ConsumerRecord<?, ?>> first = firstRecords.iterator();
    assertThat(first.next().value()).isEqualTo("first");
    Span firstSpan = Span.current();

    try (Scope ignored = Context.root().makeCurrent()) {
      producer.send(new ProducerRecord<>(SHARED_TOPIC, "second")).get(5, SECONDS);
    }
    ConsumerRecords<?, ?> secondRecords = poll(Duration.ofSeconds(5));
    assertThat(secondRecords).isNotSameAs(firstRecords);
    Iterator<? extends ConsumerRecord<?, ?>> second = secondRecords.iterator();
    assertThat(second.next().value()).isEqualTo("second");
    assertThat(Span.current().getSpanContext().getSpanId())
        .isNotEqualTo(firstSpan.getSpanContext().getSpanId());
    assertThat(second.hasNext()).isFalse();
    assertThat(Span.current().getSpanContext().getSpanId())
        .isEqualTo(firstSpan.getSpanContext().getSpanId());
    assertThat(first.hasNext()).isFalse();
    assertThat(Span.current().getSpanContext().isValid()).isFalse();

    String processName = spanName("process");
    assertThat(testing.spans())
        .filteredOn(span -> span.getName().equals(processName))
        .hasSize(2)
        .allSatisfy(
            span ->
                assertThat(span.getParentSpanId())
                    .isNotEqualTo(firstSpan.getSpanContext().getSpanId()));
  }

  @Test
  void testRecordsWithTopicPartitionKafkaConsume() throws Exception {
    String greeting = "Hello from MockConsumer!";
    producer.send(new ProducerRecord<>(SHARED_TOPIC, PARTITION, null, greeting)).get(5, SECONDS);

    testing.waitForTraces(1);

    awaitUntilConsumerIsReady();
    ConsumerRecords<?, ?> consumerRecords = poll(Duration.ofSeconds(5));
    List<? extends ConsumerRecord<?, ?>> recordsInPartition =
        consumerRecords.records(KafkaClientBaseTest.TOPIC_PARTITION);
    assertThat(recordsInPartition).hasSize(1);

    // iterate over records to generate spans
    for (ConsumerRecord<?, ?> record : recordsInPartition) {
      assertThat(record.value()).isEqualTo(greeting);
      assertThat(record.key()).isNull();
    }

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(spanName("send"))
                        .hasKind(SpanKind.PRODUCER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(sendAttributes(null, greeting, false)),
                span ->
                    span.hasName(spanName("process"))
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            processAttributes(null, greeting, false, false))));
  }

  private static String spanName(String operationName) {
    return operationName + " " + SHARED_TOPIC;
  }
}
