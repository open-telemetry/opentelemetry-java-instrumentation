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
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

// ordering is needed to ensure that the error case runs last - throwing errors in the batch handler
// is possible and tolerated, but it messes up the internal state of the vertx kafka consumer
@TestMethodOrder(OrderAnnotation.class)
public abstract class AbstractBatchRecordsVertxKafkaTest extends AbstractVertxKafkaTest {

  final CountDownLatch consumerReady = new CountDownLatch(1);

  @BeforeAll
  void setUpTopicAndConsumer() {
    // in Vertx, a batch handler is something that runs in addition to the regular single record
    // handler -- the KafkaConsumer won't start polling unless you set the regular handler
    kafkaConsumer.batchHandler(BatchRecordsHandler.INSTANCE);
    kafkaConsumer.handler(record -> testing().runWithSpan("process " + record.value(), () -> {}));

    kafkaConsumer.partitionsAssignedHandler(partitions -> consumerReady.countDown());
    subscribe("testBatchTopic");
  }

  @Order(1)
  @Test
  void shouldCreateSpansForBatchReceiveAndProcess() throws InterruptedException {
    assertTrue(consumerReady.await(30, TimeUnit.SECONDS));

    KafkaProducerRecord<String, String> record1 =
        KafkaProducerRecord.create("testBatchTopic", "10", "testSpan1");
    KafkaProducerRecord<String, String> record2 =
        KafkaProducerRecord.create("testBatchTopic", "20", "testSpan2");
    sendBatchMessages(record1, record2);

    AtomicReference<SpanData> producer1 = new AtomicReference<>();
    AtomicReference<SpanData> producer2 = new AtomicReference<>();

    testing()
        .waitAndAssertSortedTraces(
            orderByRootSpanKind(SpanKind.INTERNAL, SpanKind.CONSUMER),
            trace -> {
              trace.hasSpansSatisfyingExactlyInAnyOrder(
                  span -> span.hasName("producer"),
                  span ->
                      span.hasName("testBatchTopic publish")
                          .hasKind(SpanKind.PRODUCER)
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfyingExactly(sendAttributes(record1)),
                  span ->
                      span.hasName("testBatchTopic publish")
                          .hasKind(SpanKind.PRODUCER)
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfyingExactly(sendAttributes(record2)));

              producer1.set(trace.getSpan(1));
              producer2.set(trace.getSpan(2));
            },
            trace ->
                trace.hasSpansSatisfyingExactlyInAnyOrder(
                    span ->
                        span.hasName("testBatchTopic receive")
                            .hasKind(SpanKind.CONSUMER)
                            .hasNoParent()
                            .hasAttributesSatisfyingExactly(receiveAttributes("testBatchTopic")),

                    // batch consumer
                    span ->
                        span.hasName("testBatchTopic process")
                            .hasKind(SpanKind.CONSUMER)
                            .hasParent(trace.getSpan(0))
                            .hasLinks(
                                LinkData.create(producer1.get().getSpanContext()),
                                LinkData.create(producer2.get().getSpanContext()))
                            .hasAttributesSatisfyingExactly(
                                batchProcessAttributes("testBatchTopic")),
                    span -> span.hasName("batch consumer").hasParent(trace.getSpan(1)),

                    // single consumer 1
                    span ->
                        span.hasName("testBatchTopic process")
                            .hasKind(SpanKind.CONSUMER)
                            .hasParent(trace.getSpan(0))
                            .hasLinks(LinkData.create(producer1.get().getSpanContext()))
                            .hasAttributesSatisfyingExactly(processAttributes(record1)),
                    span -> span.hasName("process testSpan1").hasParent(trace.getSpan(3)),

                    // single consumer 2
                    span ->
                        span.hasName("testBatchTopic process")
                            .hasKind(SpanKind.CONSUMER)
                            .hasParent(trace.getSpan(0))
                            .hasLinks(LinkData.create(producer2.get().getSpanContext()))
                            .hasAttributesSatisfyingExactly(processAttributes(record2)),
                    span -> span.hasName("process testSpan2").hasParent(trace.getSpan(5))));
  }

  @Order(2)
  @Test
  void shouldHandleFailureInKafkaBatchListener() throws InterruptedException {
    assertTrue(consumerReady.await(30, TimeUnit.SECONDS));

    KafkaProducerRecord<String, String> record =
        KafkaProducerRecord.create("testBatchTopic", "10", "error");
    sendBatchMessages(record);
    // make sure that the consumer eats up any leftover records
    kafkaConsumer.resume();

    AtomicReference<SpanData> producer = new AtomicReference<>();

    // the regular handler is not being called if the batch one fails
    testing()
        .waitAndAssertSortedTraces(
            orderByRootSpanKind(SpanKind.INTERNAL, SpanKind.CONSUMER),
            trace -> {
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("producer"),
                  span ->
                      span.hasName("testBatchTopic publish")
                          .hasKind(SpanKind.PRODUCER)
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfyingExactly(sendAttributes(record)));

              producer.set(trace.getSpan(1));
            },
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("testBatchTopic receive")
                            .hasKind(SpanKind.CONSUMER)
                            .hasNoParent()
                            .hasAttributesSatisfyingExactly(receiveAttributes("testBatchTopic")),

                    // batch consumer
                    span ->
                        span.hasName("testBatchTopic process")
                            .hasKind(SpanKind.CONSUMER)
                            .hasParent(trace.getSpan(0))
                            .hasLinks(LinkData.create(producer.get().getSpanContext()))
                            .hasStatus(StatusData.error())
                            .hasException(new IllegalArgumentException("boom"))
                            .hasAttributesSatisfyingExactly(
                                batchProcessAttributes("testBatchTopic")),
                    span -> span.hasName("batch consumer").hasParent(trace.getSpan(1)),

                    // single consumer
                    span ->
                        span.hasName("testBatchTopic process")
                            .hasKind(SpanKind.CONSUMER)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(processAttributes(record)),
                    span -> span.hasName("process error").hasParent(trace.getSpan(3))));
  }
}
