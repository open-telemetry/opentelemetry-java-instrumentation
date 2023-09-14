/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.testing;

import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanKind;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.SemanticAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.AbstractLongAssert;
import org.junit.jupiter.api.Test;

public abstract class AbstractSpringKafkaNoReceiveTelemetryTest extends AbstractSpringKafkaTest {

  @Test
  void shouldCreateSpansForSingleRecordProcess() {
    testing()
        .runWithSpan(
            "producer",
            () -> {
              kafkaTemplate.executeInTransaction(
                  ops -> {
                    send("testSingleTopic", "10", "testSpan");
                    return 0;
                  });
            });

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("producer"),
                    span ->
                        span.hasName("testSingleTopic publish")
                            .hasKind(SpanKind.PRODUCER)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                equalTo(SemanticAttributes.MESSAGING_SYSTEM, "kafka"),
                                equalTo(
                                    SemanticAttributes.MESSAGING_DESTINATION_NAME,
                                    "testSingleTopic"),
                                satisfies(
                                    SemanticAttributes.MESSAGING_CLIENT_ID,
                                    stringAssert -> stringAssert.startsWith("producer")),
                                satisfies(
                                    SemanticAttributes.MESSAGING_KAFKA_DESTINATION_PARTITION,
                                    AbstractLongAssert::isNotNegative),
                                satisfies(
                                    SemanticAttributes.MESSAGING_KAFKA_MESSAGE_OFFSET,
                                    AbstractLongAssert::isNotNegative),
                                equalTo(SemanticAttributes.MESSAGING_KAFKA_MESSAGE_KEY, "10")),
                    span ->
                        span.hasName("testSingleTopic process")
                            .hasKind(SpanKind.CONSUMER)
                            .hasParent(trace.getSpan(1))
                            .hasAttributesSatisfyingExactly(
                                equalTo(SemanticAttributes.MESSAGING_SYSTEM, "kafka"),
                                equalTo(
                                    SemanticAttributes.MESSAGING_DESTINATION_NAME,
                                    "testSingleTopic"),
                                equalTo(SemanticAttributes.MESSAGING_OPERATION, "process"),
                                satisfies(
                                    SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES,
                                    AbstractLongAssert::isNotNegative),
                                satisfies(
                                    SemanticAttributes.MESSAGING_KAFKA_DESTINATION_PARTITION,
                                    AbstractLongAssert::isNotNegative),
                                satisfies(
                                    SemanticAttributes.MESSAGING_KAFKA_MESSAGE_OFFSET,
                                    AbstractLongAssert::isNotNegative),
                                equalTo(SemanticAttributes.MESSAGING_KAFKA_MESSAGE_KEY, "10"),
                                equalTo(
                                    SemanticAttributes.MESSAGING_KAFKA_CONSUMER_GROUP,
                                    "testSingleListener"),
                                satisfies(
                                    SemanticAttributes.MESSAGING_CLIENT_ID,
                                    stringAssert -> stringAssert.startsWith("consumer"))),
                    span -> span.hasName("consumer").hasParent(trace.getSpan(2))));
  }

  @Test
  void shouldHandleFailureInKafkaListener() {
    testing()
        .runWithSpan(
            "producer",
            () -> {
              kafkaTemplate.executeInTransaction(
                  ops -> {
                    send("testSingleTopic", "10", "error");
                    return 0;
                  });
            });

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("producer"),
                    span ->
                        span.hasName("testSingleTopic publish")
                            .hasKind(SpanKind.PRODUCER)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                equalTo(SemanticAttributes.MESSAGING_SYSTEM, "kafka"),
                                equalTo(
                                    SemanticAttributes.MESSAGING_DESTINATION_NAME,
                                    "testSingleTopic"),
                                satisfies(
                                    SemanticAttributes.MESSAGING_CLIENT_ID,
                                    stringAssert -> stringAssert.startsWith("producer")),
                                satisfies(
                                    SemanticAttributes.MESSAGING_KAFKA_DESTINATION_PARTITION,
                                    AbstractLongAssert::isNotNegative),
                                satisfies(
                                    SemanticAttributes.MESSAGING_KAFKA_MESSAGE_OFFSET,
                                    AbstractLongAssert::isNotNegative),
                                equalTo(SemanticAttributes.MESSAGING_KAFKA_MESSAGE_KEY, "10")),
                    span ->
                        span.hasName("testSingleTopic process")
                            .hasKind(SpanKind.CONSUMER)
                            .hasParent(trace.getSpan(1))
                            .hasStatus(StatusData.error())
                            .hasException(new IllegalArgumentException("boom"))
                            .hasAttributesSatisfyingExactly(
                                equalTo(SemanticAttributes.MESSAGING_SYSTEM, "kafka"),
                                equalTo(
                                    SemanticAttributes.MESSAGING_DESTINATION_NAME,
                                    "testSingleTopic"),
                                equalTo(SemanticAttributes.MESSAGING_OPERATION, "process"),
                                satisfies(
                                    SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES,
                                    AbstractLongAssert::isNotNegative),
                                satisfies(
                                    SemanticAttributes.MESSAGING_KAFKA_DESTINATION_PARTITION,
                                    AbstractLongAssert::isNotNegative),
                                satisfies(
                                    SemanticAttributes.MESSAGING_KAFKA_MESSAGE_OFFSET,
                                    AbstractLongAssert::isNotNegative),
                                equalTo(SemanticAttributes.MESSAGING_KAFKA_MESSAGE_KEY, "10"),
                                equalTo(
                                    SemanticAttributes.MESSAGING_KAFKA_CONSUMER_GROUP,
                                    "testSingleListener"),
                                satisfies(
                                    SemanticAttributes.MESSAGING_CLIENT_ID,
                                    stringAssert -> stringAssert.startsWith("consumer"))),
                    span -> span.hasName("consumer").hasParent(trace.getSpan(2))));
  }

  @Test
  void shouldCreateSpansForBatchReceiveAndProcess() throws InterruptedException {
    Map<String, String> batchMessages = new HashMap<>();
    batchMessages.put("10", "testSpan1");
    batchMessages.put("20", "testSpan2");
    sendBatchMessages(batchMessages);

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
                          .hasAttributesSatisfyingExactly(
                              equalTo(SemanticAttributes.MESSAGING_SYSTEM, "kafka"),
                              equalTo(
                                  SemanticAttributes.MESSAGING_DESTINATION_NAME, "testBatchTopic"),
                              satisfies(
                                  SemanticAttributes.MESSAGING_CLIENT_ID,
                                  stringAssert -> stringAssert.startsWith("producer")),
                              satisfies(
                                  SemanticAttributes.MESSAGING_KAFKA_DESTINATION_PARTITION,
                                  AbstractLongAssert::isNotNegative),
                              satisfies(
                                  SemanticAttributes.MESSAGING_KAFKA_MESSAGE_OFFSET,
                                  AbstractLongAssert::isNotNegative),
                              equalTo(SemanticAttributes.MESSAGING_KAFKA_MESSAGE_KEY, "10")),
                  span ->
                      span.hasName("testBatchTopic publish")
                          .hasKind(SpanKind.PRODUCER)
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfyingExactly(
                              equalTo(SemanticAttributes.MESSAGING_SYSTEM, "kafka"),
                              equalTo(
                                  SemanticAttributes.MESSAGING_DESTINATION_NAME, "testBatchTopic"),
                              satisfies(
                                  SemanticAttributes.MESSAGING_CLIENT_ID,
                                  stringAssert -> stringAssert.startsWith("producer")),
                              satisfies(
                                  SemanticAttributes.MESSAGING_KAFKA_DESTINATION_PARTITION,
                                  AbstractLongAssert::isNotNegative),
                              satisfies(
                                  SemanticAttributes.MESSAGING_KAFKA_MESSAGE_OFFSET,
                                  AbstractLongAssert::isNotNegative),
                              equalTo(SemanticAttributes.MESSAGING_KAFKA_MESSAGE_KEY, "20")));

              producer1.set(trace.getSpan(1));
              producer2.set(trace.getSpan(2));
            },
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("testBatchTopic process")
                            .hasKind(SpanKind.CONSUMER)
                            .hasNoParent()
                            .hasLinksSatisfying(
                                links(
                                    producer1.get().getSpanContext(),
                                    producer2.get().getSpanContext()))
                            .hasAttributesSatisfyingExactly(
                                equalTo(SemanticAttributes.MESSAGING_SYSTEM, "kafka"),
                                equalTo(
                                    SemanticAttributes.MESSAGING_DESTINATION_NAME,
                                    "testBatchTopic"),
                                equalTo(SemanticAttributes.MESSAGING_OPERATION, "process"),
                                equalTo(
                                    SemanticAttributes.MESSAGING_KAFKA_CONSUMER_GROUP,
                                    "testBatchListener"),
                                satisfies(
                                    SemanticAttributes.MESSAGING_CLIENT_ID,
                                    stringAssert -> stringAssert.startsWith("consumer"))),
                    span -> span.hasName("consumer").hasParent(trace.getSpan(0))));
  }

  @Test
  void shouldHandleFailureInKafkaBatchListener() {
    testing()
        .runWithSpan(
            "producer",
            () -> {
              kafkaTemplate.executeInTransaction(
                  ops -> {
                    send("testBatchTopic", "10", "error");
                    return 0;
                  });
            });

    AtomicReference<SpanData> producer = new AtomicReference<>();

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
                          .hasAttributesSatisfyingExactly(
                              equalTo(SemanticAttributes.MESSAGING_SYSTEM, "kafka"),
                              equalTo(
                                  SemanticAttributes.MESSAGING_DESTINATION_NAME, "testBatchTopic"),
                              satisfies(
                                  SemanticAttributes.MESSAGING_CLIENT_ID,
                                  stringAssert -> stringAssert.startsWith("producer")),
                              satisfies(
                                  SemanticAttributes.MESSAGING_KAFKA_DESTINATION_PARTITION,
                                  AbstractLongAssert::isNotNegative),
                              satisfies(
                                  SemanticAttributes.MESSAGING_KAFKA_MESSAGE_OFFSET,
                                  AbstractLongAssert::isNotNegative),
                              equalTo(SemanticAttributes.MESSAGING_KAFKA_MESSAGE_KEY, "10")));

              producer.set(trace.getSpan(1));
            },
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("testBatchTopic process")
                            .hasKind(SpanKind.CONSUMER)
                            .hasNoParent()
                            .hasLinksSatisfying(links(producer.get().getSpanContext()))
                            .hasStatus(StatusData.error())
                            .hasException(new IllegalArgumentException("boom"))
                            .hasAttributesSatisfyingExactly(
                                equalTo(SemanticAttributes.MESSAGING_SYSTEM, "kafka"),
                                equalTo(
                                    SemanticAttributes.MESSAGING_DESTINATION_NAME,
                                    "testBatchTopic"),
                                equalTo(SemanticAttributes.MESSAGING_OPERATION, "process"),
                                equalTo(
                                    SemanticAttributes.MESSAGING_KAFKA_CONSUMER_GROUP,
                                    "testBatchListener"),
                                satisfies(
                                    SemanticAttributes.MESSAGING_CLIENT_ID,
                                    stringAssert -> stringAssert.startsWith("consumer"))),
                    span -> span.hasName("consumer").hasParent(trace.getSpan(0))));
  }
}
