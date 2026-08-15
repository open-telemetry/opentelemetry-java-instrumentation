/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.kafka;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.instrumentation.testing.junit.messaging.KafkaMessagingMetricsAssertions.assertProcessDurationMetrics;
import static io.opentelemetry.instrumentation.testing.junit.messaging.KafkaMessagingMetricsAssertions.assertReceiveDurationMetrics;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanKind;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public abstract class AbstractSingleRecordVertxKafkaTest extends AbstractVertxKafkaTest {

  private final CountDownLatch consumerReady = new CountDownLatch(1);

  @BeforeAll
  void setUpTopicAndConsumer() {
    kafkaConsumer.handler(
        record -> {
          try {
            testing().runWithSpan("consumer", () -> {});
            if ("error".equals(record.value())) {
              throw new IllegalArgumentException("boom");
            }
          } finally {
            kafkaConsumer.pause();
          }
        });

    kafkaConsumer.partitionsAssignedHandler(
        partitions -> {
          kafkaConsumer.pause();
          consumerReady.countDown();
        });
    subscribe("testSingleTopic");
  }

  @Test
  void shouldCreateSpansForSingleRecordProcess() throws InterruptedException {
    assertThat(consumerReady.await(30, SECONDS)).isTrue();

    KafkaProducerRecord<String, String> record =
        KafkaProducerRecord.create("testSingleTopic", "10", "testSpan");
    sendSingleRecord(record);

    AtomicReference<SpanData> producer = new AtomicReference<>();

    if (emitStableMessagingSemconv()) {
      waitAndAssertStableTraces(
          orderByRootSpanKind(SpanKind.INTERNAL, SpanKind.CLIENT),
          trace -> {
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("producer"),
                span ->
                    span.hasName(spanName("testSingleTopic", "publish", "send"))
                        .hasKind(SpanKind.PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(sendAttributes(record)),
                span ->
                    span.hasName(spanName("testSingleTopic", "process", "process"))
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(1))
                        .hasLinks(LinkData.create(trace.getSpan(1).getSpanContext()))
                        .hasAttributesSatisfyingExactly(processAttributes(record)),
                span -> span.hasName("consumer").hasParent(trace.getSpan(2)));
            producer.set(trace.getSpan(1));
          },
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName(spanName("testSingleTopic", "receive", "poll"))
                          .hasKind(SpanKind.CLIENT)
                          .hasNoParent()
                          .hasLinks(batchRecordLink(producer.get()))
                          .hasAttributesSatisfyingExactly(receiveAttributes("testSingleTopic"))));
      assertSingleMetrics(null);
      return;
    }

    testing()
        .waitAndAssertSortedTraces(
            orderByRootSpanKind(SpanKind.INTERNAL, receiveKind()),
            trace -> {
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("producer"),
                  span ->
                      span.hasName(spanName("testSingleTopic", "publish", "send"))
                          .hasKind(SpanKind.PRODUCER)
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfyingExactly(sendAttributes(record)));

              producer.set(trace.getSpan(1));
            },
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName(spanName("testSingleTopic", "receive", "poll"))
                            .hasKind(receiveKind())
                            .hasNoParent()
                            .hasAttributesSatisfyingExactly(receiveAttributes("testSingleTopic")),
                    span ->
                        span.hasName(spanName("testSingleTopic", "process", "process"))
                            .hasKind(SpanKind.CONSUMER)
                            .hasParent(trace.getSpan(0))
                            .hasLinks(LinkData.create(producer.get().getSpanContext()))
                            .hasAttributesSatisfyingExactly(processAttributes(record)),
                    span -> span.hasName("consumer").hasParent(trace.getSpan(1))));
    assertSingleMetrics(null);
  }

  @Test
  void shouldHandleFailureInSingleRecordHandler() throws InterruptedException {
    assertThat(consumerReady.await(30, SECONDS)).isTrue();

    KafkaProducerRecord<String, String> record =
        KafkaProducerRecord.create("testSingleTopic", "10", "error");
    sendSingleRecord(record);

    AtomicReference<SpanData> producer = new AtomicReference<>();

    if (emitStableMessagingSemconv()) {
      waitAndAssertStableTraces(
          orderByRootSpanKind(SpanKind.INTERNAL, SpanKind.CLIENT),
          trace -> {
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("producer"),
                span ->
                    span.hasName(spanName("testSingleTopic", "publish", "send"))
                        .hasKind(SpanKind.PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(sendAttributes(record)),
                span ->
                    span.hasName(spanName("testSingleTopic", "process", "process"))
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(1))
                        .hasLinks(LinkData.create(trace.getSpan(1).getSpanContext()))
                        .hasStatus(StatusData.error())
                        .hasException(new IllegalArgumentException("boom"))
                        .hasAttributesSatisfyingExactly(withErrorType(processAttributes(record))),
                span -> span.hasName("consumer").hasParent(trace.getSpan(2)));
            producer.set(trace.getSpan(1));
          },
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName(spanName("testSingleTopic", "receive", "poll"))
                          .hasKind(SpanKind.CLIENT)
                          .hasNoParent()
                          .hasLinks(batchRecordLink(producer.get()))
                          .hasAttributesSatisfyingExactly(receiveAttributes("testSingleTopic"))));
      assertSingleMetrics(IllegalArgumentException.class.getName());
      return;
    }

    testing()
        .waitAndAssertSortedTraces(
            orderByRootSpanKind(SpanKind.INTERNAL, receiveKind()),
            trace -> {
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("producer"),
                  span ->
                      span.hasName(spanName("testSingleTopic", "publish", "send"))
                          .hasKind(SpanKind.PRODUCER)
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfyingExactly(sendAttributes(record)));

              producer.set(trace.getSpan(1));
            },
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName(spanName("testSingleTopic", "receive", "poll"))
                            .hasKind(receiveKind())
                            .hasNoParent()
                            .hasAttributesSatisfyingExactly(receiveAttributes("testSingleTopic")),
                    span ->
                        span.hasName(spanName("testSingleTopic", "process", "process"))
                            .hasKind(SpanKind.CONSUMER)
                            .hasParent(trace.getSpan(0))
                            .hasLinks(LinkData.create(producer.get().getSpanContext()))
                            .hasStatus(StatusData.error())
                            .hasException(new IllegalArgumentException("boom"))
                            .hasAttributesSatisfyingExactly(
                                withErrorType(processAttributes(record))),
                    span -> span.hasName("consumer").hasParent(trace.getSpan(1))));
    assertSingleMetrics(IllegalArgumentException.class.getName());
  }

  private void assertSingleMetrics(String errorType) {
    String group = hasConsumerGroup() ? "test" : null;
    // the receive operation records poll duration whether or not it produced a receive span
    assertReceiveDurationMetrics(
        testing(), "io.opentelemetry.kafka-clients-0.11", "testSingleTopic", group, null, 1, null);
    assertProcessDurationMetrics(
        testing(),
        "io.opentelemetry.vertx-kafka-client-3.6",
        "testSingleTopic",
        group,
        "0",
        1,
        errorType);
  }

  private void sendSingleRecord(KafkaProducerRecord<String, String> record)
      throws InterruptedException {
    // Wait for the poll that was in flight when the consumer paused to finish.
    Thread.sleep(1_000);
    testing().clearData();

    CountDownLatch sent = new CountDownLatch(1);
    testing().runWithSpan("producer", () -> sendRecord(record, result -> sent.countDown()));
    assertThat(sent.await(30, SECONDS)).isTrue();
    kafkaConsumer.resume();
  }
}
