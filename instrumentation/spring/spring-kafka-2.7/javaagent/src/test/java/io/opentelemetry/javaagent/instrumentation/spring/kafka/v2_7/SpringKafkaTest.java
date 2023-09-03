/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.kafka.v2_7;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanKind;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static java.util.Collections.emptyList;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.SemanticAttributes;
import io.opentelemetry.testing.AbstractSpringKafkaTest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.AbstractLongAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

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
                          equalTo(SemanticAttributes.MESSAGING_SYSTEM, "kafka"),
                          equalTo(SemanticAttributes.MESSAGING_DESTINATION_NAME, "testSingleTopic"),
                          satisfies(
                              SemanticAttributes.MESSAGING_KAFKA_DESTINATION_PARTITION,
                              AbstractLongAssert::isNotNegative),
                          satisfies(
                              SemanticAttributes.MESSAGING_KAFKA_MESSAGE_OFFSET,
                              AbstractLongAssert::isNotNegative),
                          equalTo(SemanticAttributes.MESSAGING_KAFKA_MESSAGE_KEY, "10"),
                          satisfies(
                              SemanticAttributes.MESSAGING_CLIENT_ID,
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
                            equalTo(SemanticAttributes.MESSAGING_SYSTEM, "kafka"),
                            equalTo(
                                SemanticAttributes.MESSAGING_DESTINATION_NAME, "testSingleTopic"),
                            equalTo(SemanticAttributes.MESSAGING_OPERATION, "receive"),
                            equalTo(
                                SemanticAttributes.MESSAGING_KAFKA_CONSUMER_GROUP,
                                "testSingleListener"),
                            satisfies(
                                SemanticAttributes.MESSAGING_CLIENT_ID,
                                stringAssert -> stringAssert.startsWith("consumer"))),
                span ->
                    span.hasName("testSingleTopic process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasLinks(LinkData.create(producer.get().getSpanContext()))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.MESSAGING_SYSTEM, "kafka"),
                            equalTo(
                                SemanticAttributes.MESSAGING_DESTINATION_NAME, "testSingleTopic"),
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
                          equalTo(SemanticAttributes.MESSAGING_SYSTEM, "kafka"),
                          equalTo(SemanticAttributes.MESSAGING_DESTINATION_NAME, "testSingleTopic"),
                          satisfies(
                              SemanticAttributes.MESSAGING_KAFKA_DESTINATION_PARTITION,
                              AbstractLongAssert::isNotNegative),
                          satisfies(
                              SemanticAttributes.MESSAGING_KAFKA_MESSAGE_OFFSET,
                              AbstractLongAssert::isNotNegative),
                          equalTo(SemanticAttributes.MESSAGING_KAFKA_MESSAGE_KEY, "10"),
                          satisfies(
                              SemanticAttributes.MESSAGING_CLIENT_ID,
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
                            equalTo(SemanticAttributes.MESSAGING_SYSTEM, "kafka"),
                            equalTo(
                                SemanticAttributes.MESSAGING_DESTINATION_NAME, "testSingleTopic"),
                            equalTo(SemanticAttributes.MESSAGING_OPERATION, "receive"),
                            equalTo(
                                SemanticAttributes.MESSAGING_KAFKA_CONSUMER_GROUP,
                                "testSingleListener"),
                            satisfies(
                                SemanticAttributes.MESSAGING_CLIENT_ID,
                                stringAssert -> stringAssert.startsWith("consumer"))),
                span ->
                    span.hasName("testSingleTopic process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasLinks(LinkData.create(producer.get().getSpanContext()))
                        .hasStatus(StatusData.error())
                        .hasException(new IllegalArgumentException("boom"))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.MESSAGING_SYSTEM, "kafka"),
                            equalTo(
                                SemanticAttributes.MESSAGING_DESTINATION_NAME, "testSingleTopic"),
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
                                stringAssert -> stringAssert.startsWith("consumer")),
                            satisfies(
                                longKey("kafka.record.queue_time_ms"),
                                AbstractLongAssert::isNotNegative)),
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
                          equalTo(SemanticAttributes.MESSAGING_SYSTEM, "kafka"),
                          equalTo(SemanticAttributes.MESSAGING_DESTINATION_NAME, "testBatchTopic"),
                          satisfies(
                              SemanticAttributes.MESSAGING_KAFKA_DESTINATION_PARTITION,
                              AbstractLongAssert::isNotNegative),
                          satisfies(
                              SemanticAttributes.MESSAGING_KAFKA_MESSAGE_OFFSET,
                              AbstractLongAssert::isNotNegative),
                          equalTo(SemanticAttributes.MESSAGING_KAFKA_MESSAGE_KEY, "10"),
                          satisfies(
                              SemanticAttributes.MESSAGING_CLIENT_ID,
                              stringAssert -> stringAssert.startsWith("producer"))),
              span ->
                  span.hasName("testBatchTopic publish")
                      .hasKind(SpanKind.PRODUCER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(
                          equalTo(SemanticAttributes.MESSAGING_SYSTEM, "kafka"),
                          equalTo(SemanticAttributes.MESSAGING_DESTINATION_NAME, "testBatchTopic"),
                          satisfies(
                              SemanticAttributes.MESSAGING_KAFKA_DESTINATION_PARTITION,
                              AbstractLongAssert::isNotNegative),
                          satisfies(
                              SemanticAttributes.MESSAGING_KAFKA_MESSAGE_OFFSET,
                              AbstractLongAssert::isNotNegative),
                          equalTo(SemanticAttributes.MESSAGING_KAFKA_MESSAGE_KEY, "20"),
                          satisfies(
                              SemanticAttributes.MESSAGING_CLIENT_ID,
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
                            equalTo(SemanticAttributes.MESSAGING_SYSTEM, "kafka"),
                            equalTo(
                                SemanticAttributes.MESSAGING_DESTINATION_NAME, "testBatchTopic"),
                            equalTo(SemanticAttributes.MESSAGING_OPERATION, "receive"),
                            equalTo(
                                SemanticAttributes.MESSAGING_KAFKA_CONSUMER_GROUP,
                                "testBatchListener"),
                            satisfies(
                                SemanticAttributes.MESSAGING_CLIENT_ID,
                                stringAssert -> stringAssert.startsWith("consumer"))),
                span ->
                    span.hasName("testBatchTopic process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasLinks(
                            LinkData.create(producer1.get().getSpanContext()),
                            LinkData.create(producer2.get().getSpanContext()))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.MESSAGING_SYSTEM, "kafka"),
                            equalTo(
                                SemanticAttributes.MESSAGING_DESTINATION_NAME, "testBatchTopic"),
                            equalTo(SemanticAttributes.MESSAGING_OPERATION, "process"),
                            equalTo(
                                SemanticAttributes.MESSAGING_KAFKA_CONSUMER_GROUP,
                                "testBatchListener"),
                            satisfies(
                                SemanticAttributes.MESSAGING_CLIENT_ID,
                                stringAssert -> stringAssert.startsWith("consumer"))),
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

    testing.waitAndAssertSortedTraces(
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
                          equalTo(SemanticAttributes.MESSAGING_DESTINATION_NAME, "testBatchTopic"),
                          satisfies(
                              SemanticAttributes.MESSAGING_KAFKA_DESTINATION_PARTITION,
                              AbstractLongAssert::isNotNegative),
                          satisfies(
                              SemanticAttributes.MESSAGING_KAFKA_MESSAGE_OFFSET,
                              AbstractLongAssert::isNotNegative),
                          equalTo(SemanticAttributes.MESSAGING_KAFKA_MESSAGE_KEY, "10"),
                          satisfies(
                              SemanticAttributes.MESSAGING_CLIENT_ID,
                              stringAssert -> stringAssert.startsWith("producer"))));

          producer.set(trace.getSpan(1));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("testBatchTopic receive")
                        .hasKind(SpanKind.CONSUMER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.MESSAGING_SYSTEM, "kafka"),
                            equalTo(
                                SemanticAttributes.MESSAGING_DESTINATION_NAME, "testBatchTopic"),
                            equalTo(SemanticAttributes.MESSAGING_OPERATION, "receive"),
                            equalTo(
                                SemanticAttributes.MESSAGING_KAFKA_CONSUMER_GROUP,
                                "testBatchListener"),
                            satisfies(
                                SemanticAttributes.MESSAGING_CLIENT_ID,
                                stringAssert -> stringAssert.startsWith("consumer"))),
                span ->
                    span.hasName("testBatchTopic process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasLinks(LinkData.create(producer.get().getSpanContext()))
                        .hasStatus(StatusData.error())
                        .hasException(new IllegalArgumentException("boom"))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.MESSAGING_SYSTEM, "kafka"),
                            equalTo(
                                SemanticAttributes.MESSAGING_DESTINATION_NAME, "testBatchTopic"),
                            equalTo(SemanticAttributes.MESSAGING_OPERATION, "process"),
                            equalTo(
                                SemanticAttributes.MESSAGING_KAFKA_CONSUMER_GROUP,
                                "testBatchListener"),
                            satisfies(
                                SemanticAttributes.MESSAGING_CLIENT_ID,
                                stringAssert -> stringAssert.startsWith("consumer"))),
                span -> span.hasName("consumer").hasParent(trace.getSpan(1))));
  }
}
