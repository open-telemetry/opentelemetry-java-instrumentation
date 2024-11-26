/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.kafka.v2_7;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanKind;
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
import static java.util.Collections.emptyList;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.testing.AbstractSpringKafkaTest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.assertj.core.api.AbstractLongAssert;
import org.assertj.core.api.AbstractStringAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@SuppressWarnings("deprecation") // using deprecated semconv
class SpringKafkaTest extends AbstractSpringKafkaTest {

  @RegisterExtension
  protected static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected List<Class<?>> additionalSpringConfigs() {
    return emptyList();
  }

  @Test
  void shouldCreateSpansForSingleRecordProcess() {
    testing.runWithSpan(
        "producer",
        () -> {
          kafkaTemplate.executeInTransaction(
              ops -> {
                send("testSingleTopic", "10", "testSpan");
                return 0;
              });
        });

    AtomicReference<SpanData> producer = new AtomicReference<>();

    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(SpanKind.INTERNAL, SpanKind.CONSUMER),
        trace -> {
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
                              MESSAGING_DESTINATION_PARTITION_ID, AbstractStringAssert::isNotEmpty),
                          satisfies(
                              MESSAGING_KAFKA_MESSAGE_OFFSET, AbstractLongAssert::isNotNegative),
                          equalTo(MESSAGING_KAFKA_MESSAGE_KEY, "10"),
                          satisfies(
                              MESSAGING_CLIENT_ID,
                              stringAssert -> stringAssert.startsWith("producer"))));

          producer.set(trace.getSpan(1));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("testSingleTopic receive")
                        .hasKind(SpanKind.CONSUMER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "kafka"),
                            equalTo(MESSAGING_DESTINATION_NAME, "testSingleTopic"),
                            equalTo(MESSAGING_OPERATION, "receive"),
                            equalTo(MESSAGING_KAFKA_CONSUMER_GROUP, "testSingleListener"),
                            satisfies(
                                MESSAGING_CLIENT_ID,
                                stringAssert -> stringAssert.startsWith("consumer")),
                            equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 1)),
                span ->
                    span.hasName("testSingleTopic process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasLinks(LinkData.create(producer.get().getSpanContext()))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "kafka"),
                            equalTo(MESSAGING_DESTINATION_NAME, "testSingleTopic"),
                            equalTo(MESSAGING_OPERATION, "process"),
                            satisfies(
                                MESSAGING_MESSAGE_BODY_SIZE, AbstractLongAssert::isNotNegative),
                            satisfies(
                                MESSAGING_DESTINATION_PARTITION_ID,
                                AbstractStringAssert::isNotEmpty),
                            satisfies(
                                MESSAGING_KAFKA_MESSAGE_OFFSET, AbstractLongAssert::isNotNegative),
                            equalTo(MESSAGING_KAFKA_MESSAGE_KEY, "10"),
                            equalTo(MESSAGING_KAFKA_CONSUMER_GROUP, "testSingleListener"),
                            satisfies(
                                MESSAGING_CLIENT_ID,
                                stringAssert -> stringAssert.startsWith("consumer")),
                            satisfies(
                                longKey("kafka.record.queue_time_ms"),
                                AbstractLongAssert::isNotNegative)),
                span -> span.hasName("consumer").hasParent(trace.getSpan(1))));
  }

  @Test
  void shouldHandleFailureInKafkaListener() {
    testing.runWithSpan(
        "producer",
        () -> {
          kafkaTemplate.executeInTransaction(
              ops -> {
                send("testSingleTopic", "10", "error");
                return 0;
              });
        });

    Consumer<SpanDataAssert> receiveSpanAssert =
        span ->
            span.hasName("testSingleTopic receive")
                .hasKind(SpanKind.CONSUMER)
                .hasNoParent()
                .hasAttributesSatisfyingExactly(
                    equalTo(MESSAGING_SYSTEM, "kafka"),
                    equalTo(MESSAGING_DESTINATION_NAME, "testSingleTopic"),
                    equalTo(MESSAGING_OPERATION, "receive"),
                    equalTo(MESSAGING_KAFKA_CONSUMER_GROUP, "testSingleListener"),
                    satisfies(
                        MESSAGING_CLIENT_ID, stringAssert -> stringAssert.startsWith("consumer")),
                    equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 1));
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
            satisfies(MESSAGING_CLIENT_ID, stringAssert -> stringAssert.startsWith("consumer")),
            satisfies(longKey("kafka.record.queue_time_ms"), AbstractLongAssert::isNotNegative));

    AtomicReference<SpanData> producer = new AtomicReference<>();
    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(SpanKind.INTERNAL, SpanKind.CONSUMER),
        trace -> {
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
                              MESSAGING_DESTINATION_PARTITION_ID, AbstractStringAssert::isNotEmpty),
                          satisfies(
                              MESSAGING_KAFKA_MESSAGE_OFFSET, AbstractLongAssert::isNotNegative),
                          equalTo(MESSAGING_KAFKA_MESSAGE_KEY, "10"),
                          satisfies(
                              MESSAGING_CLIENT_ID,
                              stringAssert -> stringAssert.startsWith("producer"))));

          producer.set(trace.getSpan(1));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                receiveSpanAssert,
                span ->
                    span.hasName("testSingleTopic process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasLinks(LinkData.create(producer.get().getSpanContext()))
                        .hasStatus(StatusData.error())
                        .hasException(new IllegalArgumentException("boom"))
                        .hasAttributesSatisfyingExactly(processAttributes),
                span -> span.hasName("consumer").hasParent(trace.getSpan(1))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                receiveSpanAssert,
                span ->
                    span.hasName("testSingleTopic process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasLinks(LinkData.create(producer.get().getSpanContext()))
                        .hasStatus(StatusData.error())
                        .hasException(new IllegalArgumentException("boom"))
                        .hasAttributesSatisfyingExactly(processAttributes),
                span -> span.hasName("consumer").hasParent(trace.getSpan(1))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                receiveSpanAssert,
                span ->
                    span.hasName("testSingleTopic process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasLinks(LinkData.create(producer.get().getSpanContext()))
                        .hasAttributesSatisfyingExactly(processAttributes),
                span -> span.hasName("consumer").hasParent(trace.getSpan(1))));
  }

  @Test
  void shouldCreateSpansForBatchReceiveAndProcess() throws InterruptedException {
    Map<String, String> batchMessages = new HashMap<>();
    batchMessages.put("10", "testSpan1");
    batchMessages.put("20", "testSpan2");
    sendBatchMessages(batchMessages);

    AtomicReference<SpanData> producer1 = new AtomicReference<>();
    AtomicReference<SpanData> producer2 = new AtomicReference<>();

    testing.waitAndAssertSortedTraces(
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
                              MESSAGING_DESTINATION_PARTITION_ID, AbstractStringAssert::isNotEmpty),
                          satisfies(
                              MESSAGING_KAFKA_MESSAGE_OFFSET, AbstractLongAssert::isNotNegative),
                          equalTo(MESSAGING_KAFKA_MESSAGE_KEY, "10"),
                          satisfies(
                              MESSAGING_CLIENT_ID,
                              stringAssert -> stringAssert.startsWith("producer"))),
              span ->
                  span.hasName("testBatchTopic publish")
                      .hasKind(SpanKind.PRODUCER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(
                          equalTo(MESSAGING_SYSTEM, "kafka"),
                          equalTo(MESSAGING_DESTINATION_NAME, "testBatchTopic"),
                          equalTo(MESSAGING_OPERATION, "publish"),
                          satisfies(
                              MESSAGING_DESTINATION_PARTITION_ID, AbstractStringAssert::isNotEmpty),
                          satisfies(
                              MESSAGING_KAFKA_MESSAGE_OFFSET, AbstractLongAssert::isNotNegative),
                          equalTo(MESSAGING_KAFKA_MESSAGE_KEY, "20"),
                          satisfies(
                              MESSAGING_CLIENT_ID,
                              stringAssert -> stringAssert.startsWith("producer"))));

          producer1.set(trace.getSpan(1));
          producer2.set(trace.getSpan(2));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("testBatchTopic receive")
                        .hasKind(SpanKind.CONSUMER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "kafka"),
                            equalTo(MESSAGING_DESTINATION_NAME, "testBatchTopic"),
                            equalTo(MESSAGING_OPERATION, "receive"),
                            equalTo(MESSAGING_KAFKA_CONSUMER_GROUP, "testBatchListener"),
                            satisfies(
                                MESSAGING_CLIENT_ID,
                                stringAssert -> stringAssert.startsWith("consumer")),
                            equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 2)),
                span ->
                    span.hasName("testBatchTopic process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasLinks(
                            LinkData.create(producer1.get().getSpanContext()),
                            LinkData.create(producer2.get().getSpanContext()))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "kafka"),
                            equalTo(MESSAGING_DESTINATION_NAME, "testBatchTopic"),
                            equalTo(MESSAGING_OPERATION, "process"),
                            equalTo(MESSAGING_KAFKA_CONSUMER_GROUP, "testBatchListener"),
                            satisfies(
                                MESSAGING_CLIENT_ID,
                                stringAssert -> stringAssert.startsWith("consumer")),
                            equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 2)),
                span -> span.hasName("consumer").hasParent(trace.getSpan(1))));
  }

  @Test
  void shouldHandleFailureInKafkaBatchListener() {
    testing.runWithSpan(
        "producer",
        () -> {
          kafkaTemplate.executeInTransaction(
              ops -> {
                send("testBatchTopic", "10", "error");
                return 0;
              });
        });

    AtomicReference<SpanData> producer = new AtomicReference<>();

    List<Consumer<TraceAssert>> assertions = new ArrayList<>();
    assertions.add(
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
                              MESSAGING_DESTINATION_PARTITION_ID, AbstractStringAssert::isNotEmpty),
                          satisfies(
                              MESSAGING_KAFKA_MESSAGE_OFFSET, AbstractLongAssert::isNotNegative),
                          equalTo(MESSAGING_KAFKA_MESSAGE_KEY, "10"),
                          satisfies(
                              MESSAGING_CLIENT_ID,
                              stringAssert -> stringAssert.startsWith("producer"))));

          producer.set(trace.getSpan(1));
        });

    if (Boolean.getBoolean("testLatestDeps")) {
      // latest dep tests call receive once and only retry the failed process step
      assertions.add(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  SpringKafkaTest::assertReceiveSpan,
                  span -> assertProcessSpan(span, trace, producer.get(), true),
                  span -> span.hasName("consumer").hasParent(trace.getSpan(1)),
                  span -> assertProcessSpan(span, trace, producer.get(), true),
                  span -> span.hasName("consumer").hasParent(trace.getSpan(3)),
                  span -> assertProcessSpan(span, trace, producer.get(), false),
                  span -> span.hasName("consumer").hasParent(trace.getSpan(5))));
    } else {
      assertions.addAll(
          Arrays.asList(
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      SpringKafkaTest::assertReceiveSpan,
                      span -> assertProcessSpan(span, trace, producer.get(), true),
                      span -> span.hasName("consumer").hasParent(trace.getSpan(1))),
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      SpringKafkaTest::assertReceiveSpan,
                      span -> assertProcessSpan(span, trace, producer.get(), true),
                      span -> span.hasName("consumer").hasParent(trace.getSpan(1))),
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      SpringKafkaTest::assertReceiveSpan,
                      span -> assertProcessSpan(span, trace, producer.get(), false),
                      span -> span.hasName("consumer").hasParent(trace.getSpan(1)))));
    }

    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(SpanKind.INTERNAL, SpanKind.CONSUMER), assertions);
  }

  private static void assertReceiveSpan(SpanDataAssert span) {
    span.hasName("testBatchTopic receive")
        .hasKind(SpanKind.CONSUMER)
        .hasNoParent()
        .hasAttributesSatisfyingExactly(
            equalTo(MESSAGING_SYSTEM, "kafka"),
            equalTo(MESSAGING_DESTINATION_NAME, "testBatchTopic"),
            equalTo(MESSAGING_OPERATION, "receive"),
            equalTo(MESSAGING_KAFKA_CONSUMER_GROUP, "testBatchListener"),
            satisfies(MESSAGING_CLIENT_ID, stringAssert -> stringAssert.startsWith("consumer")),
            equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 1));
  }

  private static void assertProcessSpan(
      SpanDataAssert span, TraceAssert trace, SpanData producer, boolean failed) {
    span.hasName("testBatchTopic process")
        .hasKind(SpanKind.CONSUMER)
        .hasParent(trace.getSpan(0))
        .hasLinks(LinkData.create(producer.getSpanContext()))
        .hasAttributesSatisfyingExactly(
            equalTo(MESSAGING_SYSTEM, "kafka"),
            equalTo(MESSAGING_DESTINATION_NAME, "testBatchTopic"),
            equalTo(MESSAGING_OPERATION, "process"),
            equalTo(MESSAGING_KAFKA_CONSUMER_GROUP, "testBatchListener"),
            satisfies(MESSAGING_CLIENT_ID, stringAssert -> stringAssert.startsWith("consumer")),
            equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 1));
    if (failed) {
      span.hasStatus(StatusData.error()).hasException(new IllegalArgumentException("boom"));
    }
  }
}
