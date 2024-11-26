/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.testing;

import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanKind;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanName;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_KAFKA_CONSUMER_GROUP;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_KEY;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_OFFSET;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_BODY_SIZE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.AbstractLongAssert;
import org.assertj.core.api.AbstractStringAssert;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation") // using deprecated semconv
public abstract class AbstractSpringKafkaNoReceiveTelemetryTest extends AbstractSpringKafkaTest {

  protected abstract boolean isLibraryInstrumentationTest();

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
                                equalTo(MESSAGING_SYSTEM, "kafka"),
                                equalTo(MESSAGING_DESTINATION_NAME, "testSingleTopic"),
                                equalTo(MESSAGING_OPERATION, "publish"),
                                satisfies(
                                    MESSAGING_CLIENT_ID,
                                    stringAssert -> stringAssert.startsWith("producer")),
                                satisfies(
                                    MessagingIncubatingAttributes
                                        .MESSAGING_DESTINATION_PARTITION_ID,
                                    AbstractStringAssert::isNotEmpty),
                                satisfies(
                                    MESSAGING_KAFKA_MESSAGE_OFFSET,
                                    AbstractLongAssert::isNotNegative),
                                equalTo(MESSAGING_KAFKA_MESSAGE_KEY, "10")),
                    span ->
                        span.hasName("testSingleTopic process")
                            .hasKind(SpanKind.CONSUMER)
                            .hasParent(trace.getSpan(1))
                            .hasAttributesSatisfyingExactly(
                                equalTo(MESSAGING_SYSTEM, "kafka"),
                                equalTo(MESSAGING_DESTINATION_NAME, "testSingleTopic"),
                                equalTo(MESSAGING_OPERATION, "process"),
                                satisfies(
                                    MESSAGING_MESSAGE_BODY_SIZE, AbstractLongAssert::isNotNegative),
                                satisfies(
                                    MessagingIncubatingAttributes
                                        .MESSAGING_DESTINATION_PARTITION_ID,
                                    AbstractStringAssert::isNotEmpty),
                                satisfies(
                                    MESSAGING_KAFKA_MESSAGE_OFFSET,
                                    AbstractLongAssert::isNotNegative),
                                equalTo(MESSAGING_KAFKA_MESSAGE_KEY, "10"),
                                equalTo(MESSAGING_KAFKA_CONSUMER_GROUP, "testSingleListener"),
                                satisfies(
                                    MESSAGING_CLIENT_ID,
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

    List<AttributeAssertion> processAttributes =
        Arrays.asList(
            equalTo(MESSAGING_SYSTEM, "kafka"),
            equalTo(MESSAGING_DESTINATION_NAME, "testSingleTopic"),
            equalTo(MESSAGING_OPERATION, "process"),
            satisfies(MESSAGING_MESSAGE_BODY_SIZE, AbstractLongAssert::isNotNegative),
            satisfies(MESSAGING_DESTINATION_PARTITION_ID, AbstractStringAssert::isNotEmpty),
            satisfies(MESSAGING_KAFKA_MESSAGE_OFFSET, AbstractLongAssert::isNotNegative),
            equalTo(MESSAGING_KAFKA_MESSAGE_KEY, "10"),
            equalTo(MESSAGING_KAFKA_CONSUMER_GROUP, "testSingleListener"),
            satisfies(MESSAGING_CLIENT_ID, stringAssert -> stringAssert.startsWith("consumer")));

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
                                equalTo(MESSAGING_SYSTEM, "kafka"),
                                equalTo(MESSAGING_DESTINATION_NAME, "testSingleTopic"),
                                equalTo(MESSAGING_OPERATION, "publish"),
                                satisfies(
                                    MESSAGING_CLIENT_ID,
                                    stringAssert -> stringAssert.startsWith("producer")),
                                satisfies(
                                    MessagingIncubatingAttributes
                                        .MESSAGING_DESTINATION_PARTITION_ID,
                                    AbstractStringAssert::isNotEmpty),
                                satisfies(
                                    MESSAGING_KAFKA_MESSAGE_OFFSET,
                                    AbstractLongAssert::isNotNegative),
                                equalTo(MESSAGING_KAFKA_MESSAGE_KEY, "10")),
                    span ->
                        span.hasName("testSingleTopic process")
                            .hasKind(SpanKind.CONSUMER)
                            .hasParent(trace.getSpan(1))
                            .hasStatus(StatusData.error())
                            .hasException(new IllegalArgumentException("boom"))
                            .hasAttributesSatisfyingExactly(processAttributes),
                    span -> span.hasName("consumer").hasParent(trace.getSpan(2)),
                    span ->
                        span.hasName("testSingleTopic process")
                            .hasKind(SpanKind.CONSUMER)
                            .hasParent(trace.getSpan(1))
                            .hasStatus(StatusData.error())
                            .hasException(new IllegalArgumentException("boom"))
                            .hasAttributesSatisfyingExactly(processAttributes),
                    span -> span.hasName("consumer").hasParent(trace.getSpan(4)),
                    span ->
                        span.hasName("testSingleTopic process")
                            .hasKind(SpanKind.CONSUMER)
                            .hasParent(trace.getSpan(1))
                            .hasStatus(StatusData.unset())
                            .hasAttributesSatisfyingExactly(processAttributes),
                    span -> span.hasName("consumer").hasParent(trace.getSpan(6))));
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
                              equalTo(MESSAGING_SYSTEM, "kafka"),
                              equalTo(MESSAGING_DESTINATION_NAME, "testBatchTopic"),
                              equalTo(MESSAGING_OPERATION, "publish"),
                              satisfies(
                                  MESSAGING_CLIENT_ID,
                                  stringAssert -> stringAssert.startsWith("producer")),
                              satisfies(
                                  MESSAGING_DESTINATION_PARTITION_ID,
                                  AbstractStringAssert::isNotEmpty),
                              satisfies(
                                  MESSAGING_KAFKA_MESSAGE_OFFSET,
                                  AbstractLongAssert::isNotNegative),
                              equalTo(MESSAGING_KAFKA_MESSAGE_KEY, "10")),
                  span ->
                      span.hasName("testBatchTopic publish")
                          .hasKind(SpanKind.PRODUCER)
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfyingExactly(
                              equalTo(MESSAGING_SYSTEM, "kafka"),
                              equalTo(MESSAGING_DESTINATION_NAME, "testBatchTopic"),
                              equalTo(MESSAGING_OPERATION, "publish"),
                              satisfies(
                                  MESSAGING_CLIENT_ID,
                                  stringAssert -> stringAssert.startsWith("producer")),
                              satisfies(
                                  MESSAGING_DESTINATION_PARTITION_ID,
                                  AbstractStringAssert::isNotEmpty),
                              satisfies(
                                  MESSAGING_KAFKA_MESSAGE_OFFSET,
                                  AbstractLongAssert::isNotNegative),
                              equalTo(MESSAGING_KAFKA_MESSAGE_KEY, "20")));

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
                                equalTo(MESSAGING_SYSTEM, "kafka"),
                                equalTo(MESSAGING_DESTINATION_NAME, "testBatchTopic"),
                                equalTo(MESSAGING_OPERATION, "process"),
                                equalTo(MESSAGING_KAFKA_CONSUMER_GROUP, "testBatchListener"),
                                satisfies(
                                    MESSAGING_CLIENT_ID,
                                    stringAssert -> stringAssert.startsWith("consumer")),
                                equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 2)),
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

    List<AttributeAssertion> processAttributes =
        Arrays.asList(
            equalTo(MESSAGING_SYSTEM, "kafka"),
            equalTo(MESSAGING_DESTINATION_NAME, "testBatchTopic"),
            equalTo(MESSAGING_OPERATION, "process"),
            equalTo(MESSAGING_KAFKA_CONSUMER_GROUP, "testBatchListener"),
            satisfies(MESSAGING_CLIENT_ID, stringAssert -> stringAssert.startsWith("consumer")),
            equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 1));

    testing()
        .waitAndAssertSortedTraces(
            orderByRootSpanName("producer", "testBatchTopic process", "consumer"),
            trace -> {
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("producer"),
                  span ->
                      span.hasName("testBatchTopic publish")
                          .hasKind(SpanKind.PRODUCER)
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfyingExactly(
                              equalTo(MESSAGING_SYSTEM, "kafka"),
                              equalTo(MESSAGING_DESTINATION_NAME, "testBatchTopic"),
                              equalTo(MESSAGING_OPERATION, "publish"),
                              satisfies(
                                  MESSAGING_CLIENT_ID,
                                  stringAssert -> stringAssert.startsWith("producer")),
                              satisfies(
                                  MESSAGING_DESTINATION_PARTITION_ID,
                                  AbstractStringAssert::isNotEmpty),
                              satisfies(
                                  MESSAGING_KAFKA_MESSAGE_OFFSET,
                                  AbstractLongAssert::isNotNegative),
                              equalTo(MESSAGING_KAFKA_MESSAGE_KEY, "10")));

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
                            .hasAttributesSatisfyingExactly(processAttributes),
                    span -> span.hasName("consumer").hasParent(trace.getSpan(0))),
            trace -> {
              if (isLibraryInstrumentationTest() && Boolean.getBoolean("testLatestDeps")) {
                // in latest dep tests process spans are not created for retries because spring does
                // not call the success/failure methods on the BatchInterceptor for reties
                trace.hasSpansSatisfyingExactly(span -> span.hasName("consumer").hasNoParent());
              } else {
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("testBatchTopic process")
                            .hasKind(SpanKind.CONSUMER)
                            .hasNoParent()
                            .hasLinksSatisfying(links(producer.get().getSpanContext()))
                            .hasStatus(StatusData.error())
                            .hasException(new IllegalArgumentException("boom"))
                            .hasAttributesSatisfyingExactly(processAttributes),
                    span -> span.hasName("consumer").hasParent(trace.getSpan(0)));
              }
            },
            trace -> {
              if (isLibraryInstrumentationTest() && Boolean.getBoolean("testLatestDeps")) {
                trace.hasSpansSatisfyingExactly(span -> span.hasName("consumer").hasNoParent());
              } else {
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("testBatchTopic process")
                            .hasKind(SpanKind.CONSUMER)
                            .hasNoParent()
                            .hasLinksSatisfying(links(producer.get().getSpanContext()))
                            .hasStatus(StatusData.unset())
                            .hasAttributesSatisfyingExactly(processAttributes),
                    span -> span.hasName("consumer").hasParent(trace.getSpan(0)));
              }
            });
  }
}
