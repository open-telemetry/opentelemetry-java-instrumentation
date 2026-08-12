/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanKind;
import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_CONSUMER_GROUP_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaClientBaseTest;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaClientPropagationBaseTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.assertj.core.api.AbstractStringAssert;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class KafkaClientDefaultTest extends KafkaClientPropagationBaseTest {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.kafka-clients-0.11";

  private static final double[] DURATION_BUCKETS =
      new double[] {
        0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1.0, 2.5, 5.0, 7.5, 10.0
      };

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @DisplayName("test kafka produce and consume")
  @ParameterizedTest(name = "{index} => test headers: {0}")
  @ValueSource(booleans = {true, false})
  void testKafkaProducerAndConsumerSpan(boolean testHeaders) throws Exception {
    String greeting = "Hello Kafka!";
    testing.runWithSpan(
        "parent",
        () -> {
          ProducerRecord<Integer, String> producerRecord =
              new ProducerRecord<>(SHARED_TOPIC, 10, greeting);
          if (testHeaders) {
            producerRecord.headers().add("Test-Message-Header", "test".getBytes(UTF_8));
            producerRecord.headers().add("Uncaptured-Header", "password".getBytes(UTF_8));
          }
          producer
              .send(
                  producerRecord,
                  (meta, ex) -> {
                    if (ex == null) {
                      testing.runWithSpan("producer callback", () -> {});
                    } else {
                      testing.runWithSpan("producer exception: " + ex, () -> {});
                    }
                  })
              .get(5, SECONDS);
        });

    awaitUntilConsumerIsReady();
    ConsumerRecords<?, ?> records = poll(Duration.ofSeconds(5));
    assertThat(records.count()).isEqualTo(1);

    // iterate over records to generate spans
    for (ConsumerRecord<?, ?> record : records) {
      testing.runWithSpan(
          "processing",
          () -> {
            assertThat(record.key()).isEqualTo(10);
            assertThat(record.value()).isEqualTo(greeting);
          });
    }
    AtomicReference<SpanData> producerSpan = new AtomicReference<>();
    if (emitStableMessagingSemconv()) {
      testing.waitAndAssertSortedTraces(
          orderByRootSpanKind(SpanKind.INTERNAL, SpanKind.CLIENT),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                  span -> {
                    span.hasName("send " + SHARED_TOPIC)
                        .hasKind(SpanKind.PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            sendAttributes("10", greeting, testHeaders));
                    producerSpan.set(span.actual());
                  },
                  span ->
                      span.hasName("process " + SHARED_TOPIC)
                          .hasKind(SpanKind.CONSUMER)
                          .hasParent(trace.getSpan(1))
                          .hasAttributesSatisfyingExactly(
                              processAttributes("10", greeting, testHeaders, false)),
                  span -> span.hasName("processing").hasParent(trace.getSpan(2)),
                  span ->
                      span.hasName("producer callback")
                          .hasKind(SpanKind.INTERNAL)
                          .hasParent(trace.getSpan(0))),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName("poll " + SHARED_TOPIC)
                          .hasKind(SpanKind.CLIENT)
                          .hasNoParent()
                          .hasLinks(LinkData.create(producerSpan.get().getSpanContext()))
                          .hasAttributesSatisfyingExactly(receiveAttributes(testHeaders))));
      return;
    }

    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(SpanKind.INTERNAL, SpanKind.CONSUMER),
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
              span ->
                  span.hasName(SHARED_TOPIC + " publish")
                      .hasKind(SpanKind.PRODUCER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(sendAttributes("10", greeting, testHeaders)),
              span ->
                  span.hasName("producer callback")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(0)));
          producerSpan.set(trace.getSpan(1));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(SHARED_TOPIC + " receive")
                        .hasKind(SpanKind.CONSUMER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(receiveAttributes(testHeaders)),
                span ->
                    span.hasName(SHARED_TOPIC + " process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasLinks(LinkData.create(producerSpan.get().getSpanContext()))
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            processAttributes("10", greeting, testHeaders, false)),
                span -> span.hasName("processing").hasParent(trace.getSpan(1))));
  }

  @Test
  void testMessagingMetrics() throws Exception {
    assumeTrue(emitStableMessagingSemconv());

    // polling until the consumer is ready records receive telemetry that would otherwise show up
    // as extra points below
    awaitUntilConsumerIsReady();
    testing.clearData();

    String greeting = "Hello Kafka!";
    producer.send(new ProducerRecord<>(SHARED_TOPIC, 10, greeting)).get(5, SECONDS);

    ConsumerRecords<?, ?> records = poll(Duration.ofSeconds(5));
    assertThat(records.count()).isEqualTo(1);
    // iterate over records to generate process spans
    for (ConsumerRecord<?, ?> record : records) {
      assertThat(record.value()).isEqualTo(greeting);
    }

    // consumer group is not available in version 0.11
    String consumerGroup = testLatestDeps() ? "test" : null;

    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "messaging.client.operation.duration",
        metrics ->
            metrics.satisfiesExactly(
                metric ->
                    assertThat(metric)
                        .hasUnit("s")
                        .hasDescription(
                            "Duration of messaging operation initiated by a producer or consumer client.")
                        .hasHistogramSatisfying(
                            histogram ->
                                histogram.hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasCount(1)
                                            .hasAttributesSatisfyingExactly(
                                                equalTo(MESSAGING_OPERATION_NAME, "send"),
                                                equalTo(MESSAGING_OPERATION_TYPE, "send"),
                                                equalTo(MESSAGING_SYSTEM, "kafka"),
                                                equalTo(MESSAGING_DESTINATION_NAME, SHARED_TOPIC),
                                                satisfies(
                                                    MESSAGING_DESTINATION_PARTITION_ID,
                                                    AbstractStringAssert::isNotEmpty))
                                            .hasBucketBoundaries(DURATION_BUCKETS),
                                    point ->
                                        point
                                            .hasCount(1)
                                            .hasAttributesSatisfyingExactly(
                                                equalTo(MESSAGING_OPERATION_NAME, "poll"),
                                                equalTo(MESSAGING_OPERATION_TYPE, "receive"),
                                                equalTo(MESSAGING_SYSTEM, "kafka"),
                                                equalTo(MESSAGING_DESTINATION_NAME, SHARED_TOPIC),
                                                equalTo(
                                                    MESSAGING_CONSUMER_GROUP_NAME, consumerGroup))
                                            .hasBucketBoundaries(DURATION_BUCKETS)))));

    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "messaging.client.sent.messages",
        metrics ->
            metrics.satisfiesExactly(
                metric ->
                    assertThat(metric)
                        .hasUnit("{message}")
                        .hasDescription(
                            "Number of messages producer attempted to send to the broker.")
                        .hasLongSumSatisfying(
                            sum ->
                                sum.hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasValue(1)
                                            .hasAttributesSatisfyingExactly(
                                                equalTo(MESSAGING_OPERATION_NAME, "send"),
                                                equalTo(MESSAGING_SYSTEM, "kafka"),
                                                equalTo(MESSAGING_DESTINATION_NAME, SHARED_TOPIC),
                                                satisfies(
                                                    MESSAGING_DESTINATION_PARTITION_ID,
                                                    AbstractStringAssert::isNotEmpty))))));

    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "messaging.client.consumed.messages",
        metrics ->
            metrics.satisfiesExactly(
                metric ->
                    assertThat(metric)
                        .hasUnit("{message}")
                        .hasDescription(
                            "Number of messages that were delivered to the application.")
                        .hasLongSumSatisfying(
                            sum ->
                                sum.hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasValue(1)
                                            .hasAttributesSatisfyingExactly(
                                                equalTo(MESSAGING_OPERATION_NAME, "poll"),
                                                equalTo(MESSAGING_SYSTEM, "kafka"),
                                                equalTo(MESSAGING_DESTINATION_NAME, SHARED_TOPIC),
                                                equalTo(
                                                    MESSAGING_CONSUMER_GROUP_NAME,
                                                    consumerGroup))))));

    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "messaging.process.duration",
        metrics ->
            metrics.satisfiesExactly(
                metric ->
                    assertThat(metric)
                        .hasUnit("s")
                        .hasDescription("Duration of processing operation.")
                        .hasHistogramSatisfying(
                            histogram ->
                                histogram.hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasCount(1)
                                            .hasAttributesSatisfyingExactly(
                                                equalTo(MESSAGING_OPERATION_NAME, "process"),
                                                equalTo(MESSAGING_SYSTEM, "kafka"),
                                                equalTo(MESSAGING_DESTINATION_NAME, SHARED_TOPIC),
                                                equalTo(
                                                    MESSAGING_CONSUMER_GROUP_NAME, consumerGroup),
                                                satisfies(
                                                    MESSAGING_DESTINATION_PARTITION_ID,
                                                    AbstractStringAssert::isNotEmpty))
                                            .hasBucketBoundaries(DURATION_BUCKETS)))));
  }

  @Test
  void testReceiveDoesNotParentProcessSpan() throws Exception {
    assumeTrue(emitStableMessagingSemconv());
    producer.send(new ProducerRecord<>(SHARED_TOPIC, 10, "Hello Kafka!")).get(5, SECONDS);

    awaitUntilConsumerIsReady();
    ConsumerRecords<?, ?> records = poll(Duration.ofSeconds(5));
    assertThat(records.count()).isEqualTo(1);

    for (ConsumerRecord<?, ?> ignored : records) {
      testing.runWithSpan("processing", () -> {});
    }

    AtomicReference<SpanData> producerSpan = new AtomicReference<>();
    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(SpanKind.PRODUCER, SpanKind.CLIENT),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> {
                  span.hasName("send " + SHARED_TOPIC).hasKind(SpanKind.PRODUCER).hasNoParent();
                  producerSpan.set(span.actual());
                },
                span ->
                    span.hasName("process " + SHARED_TOPIC)
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0)),
                span -> span.hasName("processing").hasParent(trace.getSpan(1))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("poll " + SHARED_TOPIC).hasKind(SpanKind.CLIENT).hasNoParent()));
  }

  @Test
  void testAbandonedIteratorDoesNotParentNextProcessSpan() throws Exception {
    assumeTrue(emitStableMessagingSemconv());
    producer.send(new ProducerRecord<>(SHARED_TOPIC, "first")).get(5, SECONDS);
    awaitUntilConsumerIsReady();
    testing.runWithSpan(
        "parent",
        () -> {
          Iterator<? extends ConsumerRecord<?, ?>> firstIterator =
              poll(Duration.ofSeconds(5)).iterator();
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
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("send " + SHARED_TOPIC).hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactlyInAnyOrder(
                span -> span.hasName("parent").hasNoParent(),
                span -> span.hasName("poll " + SHARED_TOPIC).hasParent(trace.getSpan(0)),
                span -> span.hasName("process " + SHARED_TOPIC).hasParent(trace.getSpan(0)),
                span -> span.hasName("poll " + SHARED_TOPIC).hasParent(trace.getSpan(0)),
                span -> span.hasName("process " + SHARED_TOPIC).hasParent(trace.getSpan(0))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("send " + SHARED_TOPIC).hasNoParent()));
  }

  @DisplayName("test pass through tombstone")
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

    AtomicReference<SpanData> producerSpan = new AtomicReference<>();
    if (emitStableMessagingSemconv()) {
      testing.waitAndAssertSortedTraces(
          orderByRootSpanKind(SpanKind.PRODUCER, SpanKind.CLIENT),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> {
                    span.hasName("send " + SHARED_TOPIC)
                        .hasKind(SpanKind.PRODUCER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(sendAttributes(null, null, false));
                    producerSpan.set(span.actual());
                  },
                  span ->
                      span.hasName("process " + SHARED_TOPIC)
                          .hasKind(SpanKind.CONSUMER)
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfyingExactly(
                              processAttributes(null, null, false, false))),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName("poll " + SHARED_TOPIC)
                          .hasKind(SpanKind.CLIENT)
                          .hasNoParent()
                          .hasAttributesSatisfyingExactly(receiveAttributes(false))));
      return;
    }

    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(SpanKind.INTERNAL, SpanKind.CONSUMER),
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span ->
                  span.hasName(SHARED_TOPIC + " publish")
                      .hasKind(SpanKind.PRODUCER)
                      .hasNoParent()
                      .hasAttributesSatisfyingExactly(sendAttributes(null, null, false)));
          producerSpan.set(trace.getSpan(0));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(SHARED_TOPIC + " receive")
                        .hasKind(SpanKind.CONSUMER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(receiveAttributes(false)),
                span ->
                    span.hasName(SHARED_TOPIC + " process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasLinks(LinkData.create(producerSpan.get().getSpanContext()))
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            processAttributes(null, null, false, false))));
  }

  @ParameterizedTest
  @DisplayName("test records(TopicPartition) kafka consume")
  @ValueSource(booleans = {true, false})
  void testRecordsWithTopicPartitionKafkaConsume(boolean testListIterator) throws Exception {
    String greeting = "Hello from MockConsumer!";
    producer.send(new ProducerRecord<>(SHARED_TOPIC, PARTITION, null, greeting)).get(5, SECONDS);

    testing.waitForTraces(1);

    awaitUntilConsumerIsReady();
    ConsumerRecords<?, ?> consumerRecords = poll(Duration.ofSeconds(5));
    List<? extends ConsumerRecord<?, ?>> recordsInPartition =
        consumerRecords.records(KafkaClientBaseTest.TOPIC_PARTITION);
    assertThat(recordsInPartition).hasSize(1);

    // iterate over records to generate spans
    if (testListIterator) {
      for (ListIterator<? extends ConsumerRecord<?, ?>> iterator =
              recordsInPartition.listIterator();
          iterator.hasNext(); ) {
        ConsumerRecord<?, ?> record = iterator.next();
        assertThat(record.value()).isEqualTo(greeting);
        assertThat(record.key()).isNull();
      }
    } else {
      for (ConsumerRecord<?, ?> record : recordsInPartition) {
        assertThat(record.value()).isEqualTo(greeting);
        assertThat(record.key()).isNull();
      }
    }

    AtomicReference<SpanData> producerSpan = new AtomicReference<>();
    if (emitStableMessagingSemconv()) {
      testing.waitAndAssertSortedTraces(
          orderByRootSpanKind(SpanKind.PRODUCER, SpanKind.CLIENT),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> {
                    span.hasName("send " + SHARED_TOPIC)
                        .hasKind(SpanKind.PRODUCER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(sendAttributes(null, greeting, false));
                    producerSpan.set(span.actual());
                  },
                  span ->
                      span.hasName("process " + SHARED_TOPIC)
                          .hasKind(SpanKind.CONSUMER)
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfyingExactly(
                              processAttributes(null, greeting, false, false))),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName("poll " + SHARED_TOPIC)
                          .hasKind(SpanKind.CLIENT)
                          .hasNoParent()
                          .hasAttributesSatisfyingExactly(receiveAttributes(false))));
      return;
    }

    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(SpanKind.INTERNAL, SpanKind.CONSUMER),
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span ->
                  span.hasName(SHARED_TOPIC + " publish")
                      .hasKind(SpanKind.PRODUCER)
                      .hasNoParent()
                      .hasAttributesSatisfyingExactly(sendAttributes(null, greeting, false)));
          producerSpan.set(trace.getSpan(0));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(SHARED_TOPIC + " receive")
                        .hasKind(SpanKind.CONSUMER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(receiveAttributes(false)),
                span ->
                    span.hasName(SHARED_TOPIC + " process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasLinks(LinkData.create(producerSpan.get().getSpanContext()))
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            processAttributes(null, greeting, false, false))));
  }

  @DisplayName("test kafka null header")
  @Test
  void testKafkaHeaderNull() throws Exception {
    String greeting = "Hello Kafka with null header!";
    testing.runWithSpan(
        "parent",
        () -> {
          ProducerRecord<Integer, String> producerRecord =
              new ProducerRecord<>(SHARED_TOPIC, 10, greeting);
          producerRecord.headers().add("Test-Message-Header", null);
          producer
              .send(
                  producerRecord,
                  (meta, ex) -> {
                    if (ex == null) {
                      testing.runWithSpan("producer callback", () -> {});
                    } else {
                      testing.runWithSpan("producer exception: " + ex, () -> {});
                    }
                  })
              .get(5, SECONDS);
        });

    awaitUntilConsumerIsReady();
    ConsumerRecords<?, ?> records = poll(Duration.ofSeconds(5));
    assertThat(records.count()).isEqualTo(1);

    for (ConsumerRecord<?, ?> record : records) {
      testing.runWithSpan(
          "processing",
          () -> {
            assertThat(record.key()).isEqualTo(10);
            assertThat(record.value()).isEqualTo(greeting);
            assertThat(record.headers().lastHeader("Test-Message-Header").value()).isNull();
          });
    }
    AtomicReference<SpanData> producerSpan = new AtomicReference<>();
    if (emitStableMessagingSemconv()) {
      testing.waitAndAssertSortedTraces(
          orderByRootSpanKind(SpanKind.INTERNAL, SpanKind.CLIENT),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                  span -> {
                    span.hasName("send " + SHARED_TOPIC)
                        .hasKind(SpanKind.PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(sendAttributes("10", greeting, false));
                    producerSpan.set(span.actual());
                  },
                  span ->
                      span.hasName("process " + SHARED_TOPIC)
                          .hasKind(SpanKind.CONSUMER)
                          .hasParent(trace.getSpan(1))
                          .hasAttributesSatisfyingExactly(
                              processAttributes("10", greeting, false, false)),
                  span -> span.hasName("processing").hasParent(trace.getSpan(2)),
                  span ->
                      span.hasName("producer callback")
                          .hasKind(SpanKind.INTERNAL)
                          .hasParent(trace.getSpan(0))),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName("poll " + SHARED_TOPIC)
                          .hasKind(SpanKind.CLIENT)
                          .hasNoParent()
                          .hasAttributesSatisfyingExactly(receiveAttributes(false))));
      return;
    }

    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(SpanKind.INTERNAL, SpanKind.CONSUMER),
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
              span ->
                  span.hasName(SHARED_TOPIC + " publish")
                      .hasKind(SpanKind.PRODUCER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(sendAttributes("10", greeting, false)),
              span ->
                  span.hasName("producer callback")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(0)));
          producerSpan.set(trace.getSpan(1));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(SHARED_TOPIC + " receive")
                        .hasKind(SpanKind.CONSUMER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(receiveAttributes(false)),
                span ->
                    span.hasName(SHARED_TOPIC + " process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasLinks(LinkData.create(producerSpan.get().getSpanContext()))
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            processAttributes("10", greeting, false, false)),
                span -> span.hasName("processing").hasParent(trace.getSpan(1))));
  }
}
