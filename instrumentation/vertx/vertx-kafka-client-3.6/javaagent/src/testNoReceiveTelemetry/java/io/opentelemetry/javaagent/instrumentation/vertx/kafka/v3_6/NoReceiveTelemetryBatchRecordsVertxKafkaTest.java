/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.kafka.v3_6;

import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanKind;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.AbstractLongAssert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

// ordering is needed to ensure that the error case runs last - throwing errors in the batch handler
// is possible and tolerated, but it messes up the internal state of the vertx kafka consumer
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NoReceiveTelemetryBatchRecordsVertxKafkaTest extends AbstractVertxKafkaTest {

  static final CountDownLatch consumerReady = new CountDownLatch(1);

  @BeforeAll
  static void setUpTopicAndConsumer() {
    // in Vertx, a batch handler is something that runs in addition to the regular single record
    // handler -- the KafkaConsumer won't start polling unless you set the regular handler
    kafkaConsumer.batchHandler(BatchRecordsHandler.INSTANCE);
    kafkaConsumer.handler(record -> testing.runWithSpan("process " + record.value(), () -> {}));

    kafkaConsumer.partitionsAssignedHandler(partitions -> consumerReady.countDown());
    kafkaConsumer.subscribe("testBatchTopic");
  }

  @Order(1)
  @Test
  void shouldCreateSpansForBatchReceiveAndProcess() throws InterruptedException {
    assertTrue(consumerReady.await(30, TimeUnit.SECONDS));

    sendBatchMessages(
        KafkaProducerRecord.create("testBatchTopic", "10", "testSpan1"),
        KafkaProducerRecord.create("testBatchTopic", "20", "testSpan2"));

    AtomicReference<SpanData> producer1 = new AtomicReference<>();
    AtomicReference<SpanData> producer2 = new AtomicReference<>();

    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(SpanKind.INTERNAL, SpanKind.CONSUMER),
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("producer"),

              // first record
              span ->
                  span.hasName("testBatchTopic send")
                      .hasKind(SpanKind.PRODUCER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(
                          equalTo(SemanticAttributes.MESSAGING_SYSTEM, "kafka"),
                          equalTo(SemanticAttributes.MESSAGING_DESTINATION, "testBatchTopic"),
                          equalTo(SemanticAttributes.MESSAGING_DESTINATION_KIND, "topic")),
              span ->
                  span.hasName("testBatchTopic process")
                      .hasKind(SpanKind.CONSUMER)
                      .hasParent(trace.getSpan(1))
                      .hasAttributesSatisfyingExactly(
                          equalTo(SemanticAttributes.MESSAGING_SYSTEM, "kafka"),
                          equalTo(SemanticAttributes.MESSAGING_DESTINATION, "testBatchTopic"),
                          equalTo(SemanticAttributes.MESSAGING_DESTINATION_KIND, "topic"),
                          equalTo(SemanticAttributes.MESSAGING_OPERATION, "process"),
                          satisfies(
                              SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES,
                              AbstractLongAssert::isNotNegative),
                          satisfies(
                              SemanticAttributes.MESSAGING_KAFKA_PARTITION,
                              AbstractLongAssert::isNotNegative)),
              span -> span.hasName("process testSpan1").hasParent(trace.getSpan(2)),

              // second record
              span ->
                  span.hasName("testBatchTopic send")
                      .hasKind(SpanKind.PRODUCER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(
                          equalTo(SemanticAttributes.MESSAGING_SYSTEM, "kafka"),
                          equalTo(SemanticAttributes.MESSAGING_DESTINATION, "testBatchTopic"),
                          equalTo(SemanticAttributes.MESSAGING_DESTINATION_KIND, "topic")),
              span ->
                  span.hasName("testBatchTopic process")
                      .hasKind(SpanKind.CONSUMER)
                      .hasParent(trace.getSpan(4))
                      .hasAttributesSatisfyingExactly(
                          equalTo(SemanticAttributes.MESSAGING_SYSTEM, "kafka"),
                          equalTo(SemanticAttributes.MESSAGING_DESTINATION, "testBatchTopic"),
                          equalTo(SemanticAttributes.MESSAGING_DESTINATION_KIND, "topic"),
                          equalTo(SemanticAttributes.MESSAGING_OPERATION, "process"),
                          satisfies(
                              SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES,
                              AbstractLongAssert::isNotNegative),
                          satisfies(
                              SemanticAttributes.MESSAGING_KAFKA_PARTITION,
                              AbstractLongAssert::isNotNegative)),
              span -> span.hasName("process testSpan2").hasParent(trace.getSpan(5)));

          producer1.set(trace.getSpan(1));
          producer2.set(trace.getSpan(4));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                // batch consumer
                span ->
                    span.hasName("testBatchTopic process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasNoParent()
                        .hasLinks(
                            LinkData.create(producer1.get().getSpanContext()),
                            LinkData.create(producer2.get().getSpanContext()))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.MESSAGING_SYSTEM, "kafka"),
                            equalTo(SemanticAttributes.MESSAGING_DESTINATION, "testBatchTopic"),
                            equalTo(SemanticAttributes.MESSAGING_DESTINATION_KIND, "topic"),
                            equalTo(SemanticAttributes.MESSAGING_OPERATION, "process")),
                span -> span.hasName("batch consumer").hasParent(trace.getSpan(0))));
  }

  @Order(2)
  @Test
  void shouldHandleFailureInKafkaBatchListener() throws InterruptedException {
    assertTrue(consumerReady.await(30, TimeUnit.SECONDS));

    sendBatchMessages(KafkaProducerRecord.create("testBatchTopic", "10", "error"));
    // make sure that the consumer eats up any leftover records
    kafkaConsumer.resume();

    AtomicReference<SpanData> producer = new AtomicReference<>();

    // the regular handler is not being called if the batch one fails
    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(SpanKind.INTERNAL, SpanKind.CONSUMER),
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("producer"),
              span ->
                  span.hasName("testBatchTopic send")
                      .hasKind(SpanKind.PRODUCER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(
                          equalTo(SemanticAttributes.MESSAGING_SYSTEM, "kafka"),
                          equalTo(SemanticAttributes.MESSAGING_DESTINATION, "testBatchTopic"),
                          equalTo(SemanticAttributes.MESSAGING_DESTINATION_KIND, "topic")),
              span ->
                  span.hasName("testBatchTopic process")
                      .hasKind(SpanKind.CONSUMER)
                      .hasParent(trace.getSpan(1))
                      .hasAttributesSatisfyingExactly(
                          equalTo(SemanticAttributes.MESSAGING_SYSTEM, "kafka"),
                          equalTo(SemanticAttributes.MESSAGING_DESTINATION, "testBatchTopic"),
                          equalTo(SemanticAttributes.MESSAGING_DESTINATION_KIND, "topic"),
                          equalTo(SemanticAttributes.MESSAGING_OPERATION, "process"),
                          satisfies(
                              SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES,
                              AbstractLongAssert::isNotNegative),
                          satisfies(
                              SemanticAttributes.MESSAGING_KAFKA_PARTITION,
                              AbstractLongAssert::isNotNegative)),
              span -> span.hasName("process error").hasParent(trace.getSpan(2)));

          producer.set(trace.getSpan(1));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("testBatchTopic process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasNoParent()
                        .hasLinks(LinkData.create(producer.get().getSpanContext()))
                        .hasStatus(StatusData.error())
                        .hasException(new IllegalArgumentException("boom"))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.MESSAGING_SYSTEM, "kafka"),
                            equalTo(SemanticAttributes.MESSAGING_DESTINATION, "testBatchTopic"),
                            equalTo(SemanticAttributes.MESSAGING_DESTINATION_KIND, "topic"),
                            equalTo(SemanticAttributes.MESSAGING_OPERATION, "process")),
                span -> span.hasName("batch consumer").hasParent(trace.getSpan(0))));
  }
}
