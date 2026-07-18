/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaClientBaseTest;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaClientPropagationBaseTest;
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
    ConsumerRecords<?, ?> records = poll(Duration.ofSeconds(5));
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
                    span.hasName(SHARED_TOPIC + " publish")
                        .hasKind(SpanKind.PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(sendAttributes("10", greeting, false)),
                span ->
                    span.hasName(SHARED_TOPIC + " process")
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
                    span.hasName(SHARED_TOPIC + " publish")
                        .hasKind(SpanKind.PRODUCER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(sendAttributes(null, null, false)),
                span ->
                    span.hasName(SHARED_TOPIC + " process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            processAttributes(null, null, false, false))));
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
                    span.hasName(SHARED_TOPIC + " publish")
                        .hasKind(SpanKind.PRODUCER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(sendAttributes(null, greeting, false)),
                span ->
                    span.hasName(SHARED_TOPIC + " process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            processAttributes(null, greeting, false, false))));
  }

  @Test
  void testAbandonedIteratorDoesNotParentNextProcessSpan() throws Exception {
    assumeTrue(emitStableMessagingSemconv());
    producer.send(new ProducerRecord<>(SHARED_TOPIC, "first")).get(5, SECONDS);
    awaitUntilConsumerIsReady();
    Iterator<? extends ConsumerRecord<?, ?>> firstIterator = poll(Duration.ofSeconds(5)).iterator();
    assertThat(firstIterator.hasNext()).isTrue();
    firstIterator.next();

    try (Scope ignored = Context.root().makeCurrent()) {
      producer.send(new ProducerRecord<>(SHARED_TOPIC, "second")).get(5, SECONDS);
    }
    Iterator<? extends ConsumerRecord<?, ?>> secondIterator =
        poll(Duration.ofSeconds(5)).iterator();
    assertThat(secondIterator.hasNext()).isTrue();
    secondIterator.next();
    assertThat(secondIterator.hasNext()).isFalse();
    assertThat(firstIterator.hasNext()).isFalse();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("send " + SHARED_TOPIC).hasNoParent(),
                span -> span.hasName("process " + SHARED_TOPIC).hasParent(trace.getSpan(0))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("send " + SHARED_TOPIC).hasNoParent(),
                span -> span.hasName("process " + SHARED_TOPIC).hasParent(trace.getSpan(0))));
  }
}
