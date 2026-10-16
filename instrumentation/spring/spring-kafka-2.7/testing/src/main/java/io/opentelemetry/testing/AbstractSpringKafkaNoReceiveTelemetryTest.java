/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.testing;

import static io.opentelemetry.instrumentation.testing.junit.messaging.KafkaMessagingMetricsAssertions.assertProcessDurationMetrics;
import static io.opentelemetry.instrumentation.testing.junit.messaging.KafkaMessagingMetricsAssertions.assertProcessMetricsWithConsumedMessages;
import static io.opentelemetry.instrumentation.testing.junit.messaging.KafkaMessagingMetricsAssertions.assertTotalConsumedMessages;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.asRemote;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanKind;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanName;
import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_CLIENT_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_CONSUMER_GROUP_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_KAFKA_CLUSTER_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_KEY;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_KAFKA_OFFSET;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static java.util.Arrays.asList;

import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.assertj.core.api.AbstractLongAssert;
import org.assertj.core.api.AbstractStringAssert;
import org.junit.jupiter.api.Test;

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
                        span.hasName("send testSingleTopic")
                            .hasKind(SpanKind.PRODUCER)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                sendAttributes("testSingleTopic", "10")),
                    span -> {
                      span.hasName("process testSingleTopic")
                          .hasKind(SpanKind.CONSUMER)
                          .hasParent(trace.getSpan(1))
                          .hasAttributesSatisfyingExactly(
                              singleProcessAttributes(
                                  "testSingleTopic", "testSingleListener", "10"));
                      SpanContext producerContext = trace.getSpan(1).getSpanContext();
                      span.hasLinks(LinkData.create(asRemote(producerContext)));
                    },
                    span -> span.hasName("consumer").hasParent(trace.getSpan(2))));
    assertProcessMetricsWithConsumedMessages(
        testing(),
        "io.opentelemetry.spring-kafka-2.7",
        "testSingleTopic",
        "testSingleListener",
        "0",
        1,
        1,
        null);
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
        singleProcessAttributes("testSingleTopic", "testSingleListener", "10");

    testing()
        .waitAndAssertTraces(
            trace -> {
              SpanContext producerContext = trace.getSpan(1).getSpanContext();
              List<Consumer<SpanDataAssert>> assertions =
                  new ArrayList<>(
                      asList(
                          span -> span.hasName("producer"),
                          span ->
                              span.hasName("send testSingleTopic")
                                  .hasKind(SpanKind.PRODUCER)
                                  .hasParent(trace.getSpan(0))
                                  .hasAttributesSatisfyingExactly(
                                      sendAttributes("testSingleTopic", "10")),
                          span -> {
                            span.hasName("process testSingleTopic")
                                .hasKind(SpanKind.CONSUMER)
                                .hasParent(trace.getSpan(1))
                                .hasStatus(StatusData.error())
                                .hasException(new IllegalArgumentException("boom"))
                                .hasAttributesSatisfyingExactly(withErrorType(processAttributes));
                            span.hasLinks(LinkData.create(asRemote(producerContext)));
                          },
                          span -> span.hasName("consumer").hasParent(trace.getSpan(2))));
              if (testLatestDeps()) {
                assertions.add(
                    span -> span.hasName("handle exception").hasParent(trace.getSpan(2)));
              }
              assertions.addAll(
                  asList(
                      span -> {
                        span.hasName("process testSingleTopic")
                            .hasKind(SpanKind.CONSUMER)
                            .hasParent(trace.getSpan(1))
                            .hasStatus(StatusData.error())
                            .hasException(new IllegalArgumentException("boom"))
                            .hasAttributesSatisfyingExactly(withErrorType(processAttributes));
                        span.hasLinks(LinkData.create(asRemote(producerContext)));
                      },
                      span ->
                          span.hasName("consumer")
                              .hasParent(trace.getSpan(testLatestDeps() ? 5 : 4))));
              if (testLatestDeps()) {
                assertions.add(
                    span -> span.hasName("handle exception").hasParent(trace.getSpan(5)));
              }
              assertions.addAll(
                  asList(
                      span -> {
                        span.hasName("process testSingleTopic")
                            .hasKind(SpanKind.CONSUMER)
                            .hasParent(trace.getSpan(1))
                            .hasStatus(StatusData.unset())
                            .hasAttributesSatisfyingExactly(processAttributes);
                        span.hasLinks(LinkData.create(asRemote(producerContext)));
                      },
                      span ->
                          span.hasName("consumer")
                              .hasParent(trace.getSpan(testLatestDeps() ? 8 : 6))));

              trace.hasSpansSatisfyingExactly(assertions);
            });
    assertProcessDurationMetrics(
        testing(),
        "io.opentelemetry.spring-kafka-2.7",
        "testSingleTopic",
        "testSingleListener",
        "0",
        2,
        IllegalArgumentException.class.getName());
    assertProcessDurationMetrics(
        testing(),
        "io.opentelemetry.spring-kafka-2.7",
        "testSingleTopic",
        "testSingleListener",
        "0",
        1,
        null);
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
                      span.hasName("send testBatchTopic")
                          .hasKind(SpanKind.PRODUCER)
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfyingExactly(sendAttributes("testBatchTopic", "10")),
                  span ->
                      span.hasName("send testBatchTopic")
                          .hasKind(SpanKind.PRODUCER)
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfyingExactly(sendAttributes("testBatchTopic", "20")));

              producer1.set(trace.getSpan(1));
              producer2.set(trace.getSpan(2));
            },
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("process testBatchTopic")
                            .hasKind(SpanKind.CONSUMER)
                            .hasNoParent()
                            .hasLinksSatisfying(links(producer1.get(), producer2.get()))
                            .hasAttributesSatisfyingExactly(
                                batchProcessAttributes("testBatchTopic", "testBatchListener", 2)),
                    span -> span.hasName("consumer").hasParent(trace.getSpan(0))));
    assertProcessMetricsWithConsumedMessages(
        testing(),
        "io.opentelemetry.spring-kafka-2.7",
        "testBatchTopic",
        "testBatchListener",
        "0",
        1,
        2,
        null);
    assertTotalConsumedMessages(testing(), "io.opentelemetry.spring-kafka-2.7", 2);
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
        batchProcessAttributes("testBatchTopic", "testBatchListener", 1);

    testing()
        .waitAndAssertSortedTraces(
            orderByRootSpanName("producer", "process testBatchTopic", "consumer"),
            trace -> {
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("producer"),
                  span ->
                      span.hasName("send testBatchTopic")
                          .hasKind(SpanKind.PRODUCER)
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfyingExactly(sendAttributes("testBatchTopic", "10")));

              producer.set(trace.getSpan(1));
            },
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("process testBatchTopic")
                            .hasKind(SpanKind.CONSUMER)
                            .hasNoParent()
                            .hasLinksSatisfying(links(producer.get()))
                            .hasStatus(StatusData.error())
                            .hasException(new IllegalArgumentException("boom"))
                            .hasAttributesSatisfyingExactly(withErrorType(processAttributes)),
                    span -> span.hasName("consumer").hasParent(trace.getSpan(0))),
            trace -> {
              if (isLibraryInstrumentationTest() && testLatestDeps()) {
                trace.hasSpansSatisfyingExactly(span -> span.hasName("consumer").hasNoParent());
              } else {
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("process testBatchTopic")
                            .hasKind(SpanKind.CONSUMER)
                            .hasNoParent()
                            .hasLinksSatisfying(links(producer.get()))
                            .hasStatus(StatusData.error())
                            .hasException(new IllegalArgumentException("boom"))
                            .hasAttributesSatisfyingExactly(withErrorType(processAttributes)),
                    span -> span.hasName("consumer").hasParent(trace.getSpan(0)));
              }
            },
            trace -> {
              if (isLibraryInstrumentationTest() && testLatestDeps()) {
                trace.hasSpansSatisfyingExactly(span -> span.hasName("consumer").hasNoParent());
              } else {
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("process testBatchTopic")
                            .hasKind(SpanKind.CONSUMER)
                            .hasNoParent()
                            .hasLinksSatisfying(links(producer.get()))
                            .hasStatus(StatusData.unset())
                            .hasAttributesSatisfyingExactly(processAttributes),
                    span -> span.hasName("consumer").hasParent(trace.getSpan(0)));
              }
            });
    int failureCount = isLibraryInstrumentationTest() && testLatestDeps() ? 1 : 2;
    assertProcessDurationMetrics(
        testing(),
        "io.opentelemetry.spring-kafka-2.7",
        "testBatchTopic",
        "testBatchListener",
        "0",
        failureCount,
        IllegalArgumentException.class.getName());
    if (!isLibraryInstrumentationTest() || !testLatestDeps()) {
      assertProcessDurationMetrics(
          testing(),
          "io.opentelemetry.spring-kafka-2.7",
          "testBatchTopic",
          "testBatchListener",
          "0",
          1,
          null);
    }
  }

  private static List<AttributeAssertion> sendAttributes(String topic, String messageKey) {
    List<AttributeAssertion> assertions = messagingAttributes(topic, "send", "send", "producer");
    assertions.add(satisfies(MESSAGING_DESTINATION_PARTITION_ID, AbstractStringAssert::isNotEmpty));
    assertions.add(satisfies(MESSAGING_KAFKA_OFFSET, AbstractLongAssert::isNotNegative));
    assertions.add(satisfies(MESSAGING_KAFKA_CLUSTER_ID, AbstractStringAssert::isNotEmpty));
    assertions.add(equalTo(MESSAGING_KAFKA_MESSAGE_KEY, messageKey));
    return assertions;
  }

  private static List<AttributeAssertion> singleProcessAttributes(
      String topic, String group, String messageKey) {
    List<AttributeAssertion> assertions =
        messagingAttributes(topic, "process", "process", "consumer");

    assertions.add(satisfies(MESSAGING_DESTINATION_PARTITION_ID, AbstractStringAssert::isNotEmpty));
    assertions.add(satisfies(MESSAGING_KAFKA_OFFSET, AbstractLongAssert::isNotNegative));
    assertions.add(equalTo(MESSAGING_KAFKA_MESSAGE_KEY, messageKey));
    assertions.add(equalTo(MESSAGING_CONSUMER_GROUP_NAME, group));
    assertions.add(satisfies(MESSAGING_KAFKA_CLUSTER_ID, AbstractStringAssert::isNotEmpty));
    return assertions;
  }

  private static List<AttributeAssertion> batchProcessAttributes(
      String topic, String group, int batchSize) {
    List<AttributeAssertion> assertions =
        messagingAttributes(topic, "process", "process", "consumer");
    assertions.add(equalTo(MESSAGING_CONSUMER_GROUP_NAME, group));
    assertions.add(equalTo(MESSAGING_BATCH_MESSAGE_COUNT, batchSize));
    assertions.add(satisfies(MESSAGING_KAFKA_CLUSTER_ID, AbstractStringAssert::isNotEmpty));
    assertions.add(satisfies(MESSAGING_DESTINATION_PARTITION_ID, AbstractStringAssert::isNotEmpty));
    return assertions;
  }

  private static List<AttributeAssertion> messagingAttributes(
      String topic, String operationName, String operationType, String clientIdPrefix) {
    List<AttributeAssertion> assertions =
        new ArrayList<>(
            asList(
                equalTo(MESSAGING_SYSTEM, "kafka"),
                equalTo(MESSAGING_DESTINATION_NAME, topic),
                equalTo(MESSAGING_OPERATION_NAME, operationName),
                equalTo(MESSAGING_OPERATION_TYPE, operationType)));

    assertions.add(satisfies(MESSAGING_CLIENT_ID, val -> val.startsWith(clientIdPrefix)));
    return assertions;
  }

  private static List<AttributeAssertion> withErrorType(List<AttributeAssertion> assertions) {
    List<AttributeAssertion> result = new ArrayList<>(assertions);
    result.add(equalTo(ERROR_TYPE, IllegalArgumentException.class.getName()));
    return result;
  }
}
