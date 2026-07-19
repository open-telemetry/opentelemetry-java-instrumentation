/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanKind;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class KafkaClientDefaultTest extends KafkaClientPropagationBaseTest {

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
                          .hasLinks(LinkData.create(producerSpan.get().getSpanContext()))
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
        orderByRootSpanKind(
            SpanKind.INTERNAL, emitStableMessagingSemconv() ? SpanKind.CLIENT : SpanKind.CONSUMER),
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
              span ->
                  span.hasName(
                          emitStableMessagingSemconv()
                              ? "send " + SHARED_TOPIC
                              : SHARED_TOPIC + " publish")
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
                    span.hasName(
                            emitStableMessagingSemconv()
                                ? "poll " + SHARED_TOPIC
                                : SHARED_TOPIC + " receive")
                        .hasKind(emitStableMessagingSemconv() ? SpanKind.CLIENT : SpanKind.CONSUMER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(receiveAttributes(testHeaders)),
                span ->
                    span.hasName(
                            emitStableMessagingSemconv()
                                ? "process " + SHARED_TOPIC
                                : SHARED_TOPIC + " process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasLinks(LinkData.create(producerSpan.get().getSpanContext()))
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            processAttributes("10", greeting, testHeaders, false)),
                span -> span.hasName("processing").hasParent(trace.getSpan(1))));
  }

  @Test
  void testReceiveDoesNotParentProcessSpan() throws Exception {
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
                        .hasParent(trace.getSpan(0))
                        .hasLinks(LinkData.create(producerSpan.get().getSpanContext())),
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

    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(SpanKind.PRODUCER, SpanKind.CLIENT),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("send " + SHARED_TOPIC).hasNoParent(),
                span -> span.hasName("process " + SHARED_TOPIC).hasParent(trace.getSpan(0))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("send " + SHARED_TOPIC).hasNoParent(),
                span -> span.hasName("process " + SHARED_TOPIC).hasParent(trace.getSpan(0))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("poll " + SHARED_TOPIC).hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("poll " + SHARED_TOPIC).hasNoParent()));
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
                          .hasLinks(LinkData.create(producerSpan.get().getSpanContext()))
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
        orderByRootSpanKind(
            SpanKind.INTERNAL, emitStableMessagingSemconv() ? SpanKind.CLIENT : SpanKind.CONSUMER),
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span ->
                  span.hasName(
                          emitStableMessagingSemconv()
                              ? "send " + SHARED_TOPIC
                              : SHARED_TOPIC + " publish")
                      .hasKind(SpanKind.PRODUCER)
                      .hasNoParent()
                      .hasAttributesSatisfyingExactly(sendAttributes(null, null, false)));
          producerSpan.set(trace.getSpan(0));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(
                            emitStableMessagingSemconv()
                                ? "poll " + SHARED_TOPIC
                                : SHARED_TOPIC + " receive")
                        .hasKind(emitStableMessagingSemconv() ? SpanKind.CLIENT : SpanKind.CONSUMER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(receiveAttributes(false)),
                span ->
                    span.hasName(
                            emitStableMessagingSemconv()
                                ? "process " + SHARED_TOPIC
                                : SHARED_TOPIC + " process")
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
                          .hasLinks(LinkData.create(producerSpan.get().getSpanContext()))
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
        orderByRootSpanKind(
            SpanKind.INTERNAL, emitStableMessagingSemconv() ? SpanKind.CLIENT : SpanKind.CONSUMER),
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span ->
                  span.hasName(
                          emitStableMessagingSemconv()
                              ? "send " + SHARED_TOPIC
                              : SHARED_TOPIC + " publish")
                      .hasKind(SpanKind.PRODUCER)
                      .hasNoParent()
                      .hasAttributesSatisfyingExactly(sendAttributes(null, greeting, false)));
          producerSpan.set(trace.getSpan(0));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(
                            emitStableMessagingSemconv()
                                ? "poll " + SHARED_TOPIC
                                : SHARED_TOPIC + " receive")
                        .hasKind(emitStableMessagingSemconv() ? SpanKind.CLIENT : SpanKind.CONSUMER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(receiveAttributes(false)),
                span ->
                    span.hasName(
                            emitStableMessagingSemconv()
                                ? "process " + SHARED_TOPIC
                                : SHARED_TOPIC + " process")
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
                          .hasLinks(LinkData.create(producerSpan.get().getSpanContext()))
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
        orderByRootSpanKind(
            SpanKind.INTERNAL, emitStableMessagingSemconv() ? SpanKind.CLIENT : SpanKind.CONSUMER),
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
              span ->
                  span.hasName(
                          emitStableMessagingSemconv()
                              ? "send " + SHARED_TOPIC
                              : SHARED_TOPIC + " publish")
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
                    span.hasName(
                            emitStableMessagingSemconv()
                                ? "poll " + SHARED_TOPIC
                                : SHARED_TOPIC + " receive")
                        .hasKind(emitStableMessagingSemconv() ? SpanKind.CLIENT : SpanKind.CONSUMER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(receiveAttributes(false)),
                span ->
                    span.hasName(
                            emitStableMessagingSemconv()
                                ? "process " + SHARED_TOPIC
                                : SHARED_TOPIC + " process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasLinks(LinkData.create(producerSpan.get().getSpanContext()))
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            processAttributes("10", greeting, false, false)),
                span -> span.hasName("processing").hasParent(trace.getSpan(1))));
  }
}
