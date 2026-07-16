/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.kafka.v2_7;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldMessagingSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanKind;
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

  private static final boolean EXPERIMENTAL_ATTRIBUTES =
      Boolean.getBoolean("otel.instrumentation.kafka.experimental-span-attributes");

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
        orderByRootSpanKind(SpanKind.INTERNAL, receiveKind()),
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("producer"),
              span ->
                  span.hasName(spanName("testSingleTopic", "publish", "send"))
                      .hasKind(SpanKind.PRODUCER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(producerAttributes("testSingleTopic", "10")));

          producer.set(trace.getSpan(1));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(spanName("testSingleTopic", "receive", "poll"))
                        .hasKind(receiveKind())
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            receiveAttributes("testSingleTopic", "testSingleListener", 1)),
                span ->
                    span.hasName(spanName("testSingleTopic", "process", "process"))
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasLinks(LinkData.create(producer.get().getSpanContext()))
                        .hasAttributesSatisfyingExactly(
                            singleProcessAttributes("testSingleTopic", "testSingleListener", "10")),
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
            span.hasName(spanName("testSingleTopic", "receive", "poll"))
                .hasKind(receiveKind())
                .hasNoParent()
                .hasAttributesSatisfyingExactly(
                    receiveAttributes("testSingleTopic", "testSingleListener", 1));
    List<AttributeAssertion> processAttributes =
        singleProcessAttributes("testSingleTopic", "testSingleListener", "10");

    AtomicReference<SpanData> producer = new AtomicReference<>();
    // trace structure differs in latest dep tests because CommonErrorHandler is only set for latest
    // dep tests
    if (testLatestDeps()) {
      testing.waitAndAssertSortedTraces(
          orderByRootSpanKind(SpanKind.INTERNAL, receiveKind()),
          trace -> {
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("producer"),
                span ->
                    span.hasName(spanName("testSingleTopic", "publish", "send"))
                        .hasKind(SpanKind.PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            producerAttributes("testSingleTopic", "10")));

            producer.set(trace.getSpan(1));
          },
          trace ->
              trace.hasSpansSatisfyingExactly(
                  receiveSpanAssert,
                  span ->
                      span.hasName(spanName("testSingleTopic", "process", "process"))
                          .hasKind(SpanKind.CONSUMER)
                          .hasParent(trace.getSpan(0))
                          .hasLinks(LinkData.create(producer.get().getSpanContext()))
                          .hasStatus(StatusData.error())
                          .hasException(new IllegalArgumentException("boom"))
                          .hasAttributesSatisfyingExactly(withErrorType(processAttributes, true)),
                  span -> span.hasName("consumer").hasParent(trace.getSpan(1)),
                  span -> span.hasName("handle exception").hasParent(trace.getSpan(1)),
                  span ->
                      span.hasName(spanName("testSingleTopic", "process", "process"))
                          .hasKind(SpanKind.CONSUMER)
                          .hasParent(trace.getSpan(0))
                          .hasLinks(LinkData.create(producer.get().getSpanContext()))
                          .hasStatus(StatusData.error())
                          .hasException(new IllegalArgumentException("boom"))
                          .hasAttributesSatisfyingExactly(withErrorType(processAttributes, true)),
                  span -> span.hasName("consumer").hasParent(trace.getSpan(4)),
                  span -> span.hasName("handle exception").hasParent(trace.getSpan(4)),
                  span ->
                      span.hasName(spanName("testSingleTopic", "process", "process"))
                          .hasKind(SpanKind.CONSUMER)
                          .hasParent(trace.getSpan(0))
                          .hasLinks(LinkData.create(producer.get().getSpanContext()))
                          .hasAttributesSatisfyingExactly(withErrorType(processAttributes, false)),
                  span -> span.hasName("consumer").hasParent(trace.getSpan(7))));

    } else {
      testing.waitAndAssertSortedTraces(
          orderByRootSpanKind(SpanKind.INTERNAL, receiveKind()),
          trace -> {
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("producer"),
                span ->
                    span.hasName(spanName("testSingleTopic", "publish", "send"))
                        .hasKind(SpanKind.PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            producerAttributes("testSingleTopic", "10")));

            producer.set(trace.getSpan(1));
          },
          trace ->
              trace.hasSpansSatisfyingExactly(
                  receiveSpanAssert,
                  span ->
                      span.hasName(spanName("testSingleTopic", "process", "process"))
                          .hasKind(SpanKind.CONSUMER)
                          .hasParent(trace.getSpan(0))
                          .hasLinks(LinkData.create(producer.get().getSpanContext()))
                          .hasStatus(StatusData.error())
                          .hasException(new IllegalArgumentException("boom"))
                          .hasAttributesSatisfyingExactly(withErrorType(processAttributes, true)),
                  span -> span.hasName("consumer").hasParent(trace.getSpan(1))),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  receiveSpanAssert,
                  span ->
                      span.hasName(spanName("testSingleTopic", "process", "process"))
                          .hasKind(SpanKind.CONSUMER)
                          .hasParent(trace.getSpan(0))
                          .hasLinks(LinkData.create(producer.get().getSpanContext()))
                          .hasStatus(StatusData.error())
                          .hasException(new IllegalArgumentException("boom"))
                          .hasAttributesSatisfyingExactly(withErrorType(processAttributes, true)),
                  span -> span.hasName("consumer").hasParent(trace.getSpan(1))),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  receiveSpanAssert,
                  span ->
                      span.hasName(spanName("testSingleTopic", "process", "process"))
                          .hasKind(SpanKind.CONSUMER)
                          .hasParent(trace.getSpan(0))
                          .hasLinks(LinkData.create(producer.get().getSpanContext()))
                          .hasAttributesSatisfyingExactly(withErrorType(processAttributes, false)),
                  span -> span.hasName("consumer").hasParent(trace.getSpan(1))));
    }
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
        orderByRootSpanKind(SpanKind.INTERNAL, receiveKind()),
        trace -> {
          trace.hasSpansSatisfyingExactlyInAnyOrder(
              span -> span.hasName("producer"),
              span ->
                  span.hasName(spanName("testBatchTopic", "publish", "send"))
                      .hasKind(SpanKind.PRODUCER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(producerAttributes("testBatchTopic", "10")),
              span ->
                  span.hasName(spanName("testBatchTopic", "publish", "send"))
                      .hasKind(SpanKind.PRODUCER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(producerAttributes("testBatchTopic", "20")));

          producer1.set(trace.getSpan(1));
          producer2.set(trace.getSpan(2));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(spanName("testBatchTopic", "receive", "poll"))
                        .hasKind(receiveKind())
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            receiveAttributes("testBatchTopic", "testBatchListener", 2)),
                span ->
                    span.hasName(spanName("testBatchTopic", "process", "process"))
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasLinks(
                            LinkData.create(producer1.get().getSpanContext()),
                            LinkData.create(producer2.get().getSpanContext()))
                        .hasAttributesSatisfyingExactly(
                            batchProcessAttributes("testBatchTopic", "testBatchListener", 2)),
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
                  span.hasName(spanName("testBatchTopic", "publish", "send"))
                      .hasKind(SpanKind.PRODUCER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(producerAttributes("testBatchTopic", "10")));

          producer.set(trace.getSpan(1));
        });

    if (testLatestDeps()) {
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
          asList(
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
        orderByRootSpanKind(SpanKind.INTERNAL, receiveKind()), assertions);
  }

  private static void assertReceiveSpan(SpanDataAssert span) {
    span.hasName(spanName("testBatchTopic", "receive", "poll"))
        .hasKind(receiveKind())
        .hasNoParent()
        .hasAttributesSatisfyingExactly(
            receiveAttributes("testBatchTopic", "testBatchListener", 1));
  }

  private static void assertProcessSpan(
      SpanDataAssert span, TraceAssert trace, SpanData producer, boolean failed) {
    span.hasName(spanName("testBatchTopic", "process", "process"))
        .hasKind(SpanKind.CONSUMER)
        .hasParent(trace.getSpan(0))
        .hasLinks(LinkData.create(producer.getSpanContext()))
        .hasAttributesSatisfyingExactly(
            withErrorType(
                batchProcessAttributes("testBatchTopic", "testBatchListener", 1), failed));
    if (failed) {
      span.hasStatus(StatusData.error()).hasException(new IllegalArgumentException("boom"));
    }
  }

  private static List<AttributeAssertion> producerAttributes(String topic, String messageKey) {
    List<AttributeAssertion> assertions =
        messagingAttributes(topic, "publish", "send", "send", "producer");
    assertions.add(satisfies(MESSAGING_DESTINATION_PARTITION_ID, AbstractStringAssert::isNotEmpty));
    addOffsetAssertion(assertions);
    assertions.add(equalTo(MESSAGING_KAFKA_MESSAGE_KEY, messageKey));
    assertions.add(
        equalTo(
            stringKey("messaging.kafka.bootstrap.servers"),
            EXPERIMENTAL_ATTRIBUTES ? kafka.getBootstrapServers() : null));
    return assertions;
  }

  private static List<AttributeAssertion> receiveAttributes(
      String topic, String group, int batchSize) {
    List<AttributeAssertion> assertions =
        messagingAttributes(topic, "receive", "poll", "receive", "consumer");
    addGroupAssertions(assertions, group);
    assertions.add(equalTo(MESSAGING_BATCH_MESSAGE_COUNT, batchSize));
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
    assertions.add(
        satisfies(
            longKey("kafka.record.queue_time_ms"),
            val -> {
              if (EXPERIMENTAL_ATTRIBUTES) {
                val.isNotNegative();
              }
            }));
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

  private static SpanKind receiveKind() {
    return emitStableMessagingSemconv() ? SpanKind.CLIENT : SpanKind.CONSUMER;
  }
}
