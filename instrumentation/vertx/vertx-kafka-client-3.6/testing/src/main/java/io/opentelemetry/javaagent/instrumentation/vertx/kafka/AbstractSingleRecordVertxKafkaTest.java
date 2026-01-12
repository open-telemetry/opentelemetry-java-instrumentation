/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.kafka;

import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanKind;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public abstract class AbstractSingleRecordVertxKafkaTest extends AbstractVertxKafkaTest {

  final CountDownLatch consumerReady = new CountDownLatch(1);

  @BeforeAll
  void setUpTopicAndConsumer() {
    kafkaConsumer.handler(
        record -> {
          testing().runWithSpan("consumer", () -> {});
          if (record.value().equals("error")) {
            throw new IllegalArgumentException("boom");
          }
        });

    kafkaConsumer.partitionsAssignedHandler(partitions -> consumerReady.countDown());
    subscribe("testSingleTopic");
  }

  @Test
  void shouldCreateSpansForSingleRecordProcess() throws InterruptedException {
    assertTrue(consumerReady.await(30, TimeUnit.SECONDS));

    KafkaProducerRecord<String, String> record =
        KafkaProducerRecord.create("testSingleTopic", "10", "testSpan");
    CountDownLatch sent = new CountDownLatch(1);
    testing().runWithSpan("producer", () -> sendRecord(record, result -> sent.countDown()));
    assertTrue(sent.await(30, TimeUnit.SECONDS));

    AtomicReference<SpanData> producer = new AtomicReference<>();

    testing()
        .waitAndAssertSortedTraces(
            orderByRootSpanKind(SpanKind.INTERNAL, SpanKind.CONSUMER),
            trace -> {
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("producer"),
                  span ->
                      span.hasName("testSingleTopic publish")
                          .hasKind(SpanKind.PRODUCER)
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfyingExactly(sendAttributes(record)));

              producer.set(trace.getSpan(1));
            },
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("testSingleTopic receive")
                            .hasKind(SpanKind.CONSUMER)
                            .hasNoParent()
                            .hasAttributesSatisfyingExactly(receiveAttributes("testSingleTopic")),
                    span ->
                        span.hasName("testSingleTopic process")
                            .hasKind(SpanKind.CONSUMER)
                            .hasParent(trace.getSpan(0))
                            .hasLinks(LinkData.create(producer.get().getSpanContext()))
                            .hasAttributesSatisfyingExactly(processAttributes(record)),
                    span -> span.hasName("consumer").hasParent(trace.getSpan(1))));
  }

  @Test
  void shouldHandleFailureInSingleRecordHandler() throws InterruptedException {
    assertTrue(consumerReady.await(30, TimeUnit.SECONDS));

    KafkaProducerRecord<String, String> record =
        KafkaProducerRecord.create("testSingleTopic", "10", "error");
    CountDownLatch sent = new CountDownLatch(1);
    testing().runWithSpan("producer", () -> sendRecord(record, result -> sent.countDown()));
    assertTrue(sent.await(30, TimeUnit.SECONDS));

    AtomicReference<SpanData> producer = new AtomicReference<>();

    testing()
        .waitAndAssertSortedTraces(
            orderByRootSpanKind(SpanKind.INTERNAL, SpanKind.CONSUMER),
            trace -> {
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("producer"),
                  span ->
                      span.hasName("testSingleTopic publish")
                          .hasKind(SpanKind.PRODUCER)
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfyingExactly(sendAttributes(record)));

              producer.set(trace.getSpan(1));
            },
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("testSingleTopic receive")
                            .hasKind(SpanKind.CONSUMER)
                            .hasNoParent()
                            .hasAttributesSatisfyingExactly(receiveAttributes("testSingleTopic")),
                    span ->
                        span.hasName("testSingleTopic process")
                            .hasKind(SpanKind.CONSUMER)
                            .hasParent(trace.getSpan(0))
                            .hasLinks(LinkData.create(producer.get().getSpanContext()))
                            .hasStatus(StatusData.error())
                            .hasException(new IllegalArgumentException("boom"))
                            .hasAttributesSatisfyingExactly(processAttributes(record)),
                    span -> span.hasName("consumer").hasParent(trace.getSpan(1))));
  }
}
