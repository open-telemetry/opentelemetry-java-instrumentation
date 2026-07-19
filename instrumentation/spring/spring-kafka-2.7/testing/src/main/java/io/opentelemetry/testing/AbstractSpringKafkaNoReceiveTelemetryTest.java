/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.testing;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldMessagingSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanKind;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanName;
import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_CONSUMER_GROUP_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_KAFKA_CONSUMER_GROUP;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_KEY;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_OFFSET;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_KAFKA_OFFSET;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_BODY_SIZE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static java.util.Arrays.asList;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
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
                        span.hasName(spanName("testSingleTopic", "publish", "send"))
                            .hasKind(SpanKind.PRODUCER)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                sendAttributes("testSingleTopic", "10")),
                    span ->
                        span.hasName(spanName("testSingleTopic", "process", "process"))
                            .hasKind(SpanKind.CONSUMER)
                            .hasParent(trace.getSpan(1))
                            .hasAttributesSatisfyingExactly(
                                singleProcessAttributes(
                                    "testSingleTopic", "testSingleListener", "10")),
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
        singleProcessAttributes("testSingleTopic", "testSingleListener", "10");

    testing()
        .waitAndAssertTraces(
            trace -> {
              List<Consumer<SpanDataAssert>> assertions =
                  new ArrayList<>(
                      asList(
                          span -> span.hasName("producer"),
                          span ->
                              span.hasName(spanName("testSingleTopic", "publish", "send"))
                                  .hasKind(SpanKind.PRODUCER)
                                  .hasParent(trace.getSpan(0))
                                  .hasAttributesSatisfyingExactly(
                                      sendAttributes("testSingleTopic", "10")),
                          span ->
                              span.hasName(spanName("testSingleTopic", "process", "process"))
                                  .hasKind(SpanKind.CONSUMER)
                                  .hasParent(trace.getSpan(1))
                                  .hasStatus(StatusData.error())
                                  .hasException(new IllegalArgumentException("boom"))
                                  .hasAttributesSatisfyingExactly(
                                      withErrorType(processAttributes, true)),
                          span -> span.hasName("consumer").hasParent(trace.getSpan(2))));
              if (testLatestDeps()) {
                assertions.add(
                    span -> span.hasName("handle exception").hasParent(trace.getSpan(2)));
              }
              assertions.addAll(
                  asList(
                      span ->
                          span.hasName(spanName("testSingleTopic", "process", "process"))
                              .hasKind(SpanKind.CONSUMER)
                              .hasParent(trace.getSpan(1))
                              .hasStatus(StatusData.error())
                              .hasException(new IllegalArgumentException("boom"))
                              .hasAttributesSatisfyingExactly(
                                  withErrorType(processAttributes, true)),
                      span ->
                          span.hasName("consumer")
                              .hasParent(trace.getSpan(testLatestDeps() ? 5 : 4))));
              if (testLatestDeps()) {
                assertions.add(
                    span -> span.hasName("handle exception").hasParent(trace.getSpan(5)));
              }
              assertions.addAll(
                  asList(
                      span ->
                          span.hasName(spanName("testSingleTopic", "process", "process"))
                              .hasKind(SpanKind.CONSUMER)
                              .hasParent(trace.getSpan(1))
                              .hasStatus(StatusData.unset())
                              .hasAttributesSatisfyingExactly(processAttributes),
                      span ->
                          span.hasName("consumer")
                              .hasParent(trace.getSpan(testLatestDeps() ? 8 : 6))));

              trace.hasSpansSatisfyingExactly(assertions);
            });
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
                      span.hasName(spanName("testBatchTopic", "publish", "send"))
                          .hasKind(SpanKind.PRODUCER)
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfyingExactly(sendAttributes("testBatchTopic", "10")),
                  span ->
                      span.hasName(spanName("testBatchTopic", "publish", "send"))
                          .hasKind(SpanKind.PRODUCER)
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfyingExactly(sendAttributes("testBatchTopic", "20")));

              producer1.set(trace.getSpan(1));
              producer2.set(trace.getSpan(2));
            },
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName(spanName("testBatchTopic", "process", "process"))
                            .hasKind(SpanKind.CONSUMER)
                            .hasNoParent()
                            .hasLinksSatisfying(
                                links(
                                    producer1.get().getSpanContext(),
                                    producer2.get().getSpanContext()))
                            .hasAttributesSatisfyingExactly(
                                batchProcessAttributes("testBatchTopic", "testBatchListener", 2)),
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
        batchProcessAttributes("testBatchTopic", "testBatchListener", 1);

    testing()
        .waitAndAssertSortedTraces(
            orderByRootSpanName(
                "producer", spanName("testBatchTopic", "process", "process"), "consumer"),
            trace -> {
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("producer"),
                  span ->
                      span.hasName(spanName("testBatchTopic", "publish", "send"))
                          .hasKind(SpanKind.PRODUCER)
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfyingExactly(sendAttributes("testBatchTopic", "10")));

              producer.set(trace.getSpan(1));
            },
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName(spanName("testBatchTopic", "process", "process"))
                            .hasKind(SpanKind.CONSUMER)
                            .hasNoParent()
                            .hasLinksSatisfying(links(producer.get().getSpanContext()))
                            .hasStatus(StatusData.error())
                            .hasException(new IllegalArgumentException("boom"))
                            .hasAttributesSatisfyingExactly(withErrorType(processAttributes, true)),
                    span -> span.hasName("consumer").hasParent(trace.getSpan(0))),
            trace -> {
              if (isLibraryInstrumentationTest() && testLatestDeps()) {
                // in latest dep tests process spans are not created for retries because spring does
                // not call the success/failure methods on the BatchInterceptor for retries
                trace.hasSpansSatisfyingExactly(span -> span.hasName("consumer").hasNoParent());
              } else {
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName(spanName("testBatchTopic", "process", "process"))
                            .hasKind(SpanKind.CONSUMER)
                            .hasNoParent()
                            .hasLinksSatisfying(links(producer.get().getSpanContext()))
                            .hasStatus(StatusData.error())
                            .hasException(new IllegalArgumentException("boom"))
                            .hasAttributesSatisfyingExactly(withErrorType(processAttributes, true)),
                    span -> span.hasName("consumer").hasParent(trace.getSpan(0)));
              }
            },
            trace -> {
              if (isLibraryInstrumentationTest() && testLatestDeps()) {
                trace.hasSpansSatisfyingExactly(span -> span.hasName("consumer").hasNoParent());
              } else {
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName(spanName("testBatchTopic", "process", "process"))
                            .hasKind(SpanKind.CONSUMER)
                            .hasNoParent()
                            .hasLinksSatisfying(links(producer.get().getSpanContext()))
                            .hasStatus(StatusData.unset())
                            .hasAttributesSatisfyingExactly(processAttributes),
                    span -> span.hasName("consumer").hasParent(trace.getSpan(0)));
              }
            });
  }

  private static List<AttributeAssertion> sendAttributes(String topic, String messageKey) {
    List<AttributeAssertion> assertions =
        messagingAttributes(topic, "publish", "send", "send", "producer");
    assertions.add(satisfies(MESSAGING_DESTINATION_PARTITION_ID, AbstractStringAssert::isNotEmpty));
    addOffsetAssertion(assertions);
    assertions.add(equalTo(MESSAGING_KAFKA_MESSAGE_KEY, messageKey));
    return assertions;
  }

  private static List<AttributeAssertion> singleProcessAttributes(
      String topic, String group, String messageKey) {
    List<AttributeAssertion> assertions =
        messagingAttributes(topic, "process", "process", "process", "consumer");
    assertions.add(satisfies(MESSAGING_MESSAGE_BODY_SIZE, AbstractLongAssert::isNotNegative));
    assertions.add(satisfies(MESSAGING_DESTINATION_PARTITION_ID, AbstractStringAssert::isNotEmpty));
    addOffsetAssertion(assertions);
    assertions.add(equalTo(MESSAGING_KAFKA_MESSAGE_KEY, messageKey));
    addGroupAssertions(assertions, group);
    return assertions;
  }

  private static List<AttributeAssertion> batchProcessAttributes(
      String topic, String group, int batchSize) {
    List<AttributeAssertion> assertions =
        messagingAttributes(topic, "process", "process", "process", "consumer");
    addGroupAssertions(assertions, group);
    assertions.add(equalTo(MESSAGING_BATCH_MESSAGE_COUNT, batchSize));
    return assertions;
  }

  private static List<AttributeAssertion> messagingAttributes(
      String topic,
      String oldOperation,
      String operationName,
      String operationType,
      String clientIdPrefix) {
    List<AttributeAssertion> assertions =
        new ArrayList<>(
            asList(
                equalTo(MESSAGING_SYSTEM, "kafka"),
                equalTo(MESSAGING_DESTINATION_NAME, topic),
                equalTo(MESSAGING_OPERATION, emitOldMessagingSemconv() ? oldOperation : null),
                equalTo(
                    MESSAGING_OPERATION_NAME, emitStableMessagingSemconv() ? operationName : null),
                equalTo(
                    MESSAGING_OPERATION_TYPE,
                    emitStableMessagingSemconv() ? operationType : null)));
    if (emitOldMessagingSemconv()) {
      assertions.add(
          satisfies(stringKey("messaging.client_id"), val -> val.startsWith(clientIdPrefix)));
    }
    if (emitStableMessagingSemconv()) {
      assertions.add(
          satisfies(stringKey("messaging.client.id"), val -> val.startsWith(clientIdPrefix)));
    }
    return assertions;
  }

  private static void addOffsetAssertion(List<AttributeAssertion> assertions) {
    if (emitOldMessagingSemconv()) {
      assertions.add(satisfies(MESSAGING_KAFKA_MESSAGE_OFFSET, AbstractLongAssert::isNotNegative));
    }
    if (emitStableMessagingSemconv()) {
      assertions.add(satisfies(MESSAGING_KAFKA_OFFSET, AbstractLongAssert::isNotNegative));
    }
  }

  private static void addGroupAssertions(List<AttributeAssertion> assertions, String group) {
    if (emitOldMessagingSemconv()) {
      assertions.add(equalTo(MESSAGING_KAFKA_CONSUMER_GROUP, group));
    }
    if (emitStableMessagingSemconv()) {
      assertions.add(equalTo(MESSAGING_CONSUMER_GROUP_NAME, group));
    }
  }

  private static List<AttributeAssertion> withErrorType(
      List<AttributeAssertion> assertions, boolean failed) {
    List<AttributeAssertion> result = new ArrayList<>(assertions);
    if (emitStableMessagingSemconv() && failed) {
      result.add(equalTo(ERROR_TYPE, IllegalArgumentException.class.getName()));
    }
    return result;
  }

  private static String spanName(String topic, String oldOperation, String operationName) {
    return emitStableMessagingSemconv() ? operationName + " " + topic : topic + " " + oldOperation;
  }
}
