/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.v2_6;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.api.internal.SemconvExceptionSignal.emitExceptionAsLogs;
import static io.opentelemetry.instrumentation.api.internal.SemconvExceptionSignal.emitExceptionAsSpanEvents;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldMessagingSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_CONSUMER_GROUP_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_KAFKA_CONSUMER_GROUP;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_OFFSET;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_KAFKA_OFFSET;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_BODY_SIZE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.message.MessageHeaderUtil;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.consumer.Consumer;
import org.assertj.core.api.AbstractLongAssert;
import org.assertj.core.api.AbstractStringAssert;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation") // using deprecated semconv
class WrapperTest extends AbstractWrapperTest {

  @Override
  void configure(KafkaTelemetryBuilder builder) {
    builder.setMessagingReceiveTelemetryEnabled(true);
  }

  @Override
  void assertTraces(boolean testHeaders, boolean testExperimental) {
    AtomicReference<SpanContext> producerSpanContext = new AtomicReference<>();

    if (emitStableMessagingSemconv()) {
      testing.waitAndAssertTraces(
          trace -> {
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("send " + SHARED_TOPIC)
                        .hasKind(SpanKind.PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            sendAttributes(testHeaders, testExperimental)),
                span ->
                    span.hasName("process " + SHARED_TOPIC)
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(1))
                        .hasLinks()
                        .hasAttributesSatisfyingExactly(
                            processAttributes(greeting, testHeaders, testExperimental)),
                span ->
                    span.hasName("process child")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(2)),
                span ->
                    span.hasName("producer callback")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0)));
            SpanContext spanContext = trace.getSpan(1).getSpanContext();
            producerSpanContext.set(
                SpanContext.createFromRemoteParent(
                    spanContext.getTraceId(),
                    spanContext.getSpanId(),
                    spanContext.getTraceFlags(),
                    spanContext.getTraceState()));
          },
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName("poll " + SHARED_TOPIC)
                          .hasKind(SpanKind.CLIENT)
                          .hasNoParent()
                          .hasLinks(LinkData.create(producerSpanContext.get()))
                          .hasAttributesSatisfyingExactly(receiveAttributes(testHeaders))));
      return;
    }

    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
              span ->
                  span.hasName(SHARED_TOPIC + " publish")
                      .hasKind(SpanKind.PRODUCER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(
                          sendAttributes(testHeaders, testExperimental)),
              span ->
                  span.hasName("producer callback")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(0)));
          SpanContext spanContext = trace.getSpan(1).getSpanContext();
          producerSpanContext.set(
              SpanContext.createFromRemoteParent(
                  spanContext.getTraceId(),
                  spanContext.getSpanId(),
                  spanContext.getTraceFlags(),
                  spanContext.getTraceState()));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(SHARED_TOPIC + " receive")
                        .hasKind(SpanKind.CONSUMER)
                        .hasNoParent()
                        .hasLinksSatisfying(links -> assertThat(links).isEmpty())
                        .hasAttributesSatisfyingExactly(receiveAttributes(testHeaders)),
                span ->
                    span.hasName(SHARED_TOPIC + " process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasLinks(LinkData.create(producerSpanContext.get()))
                        .hasAttributesSatisfyingExactly(
                            processAttributes(greeting, testHeaders, testExperimental)),
                span ->
                    span.hasName("process child")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(1))));
  }

  protected static List<AttributeAssertion> sendAttributes(
      boolean testHeaders, boolean testExperimental) {
    List<AttributeAssertion> assertions =
        new ArrayList<>(
            asList(
                equalTo(MESSAGING_SYSTEM, "kafka"),
                equalTo(MESSAGING_DESTINATION_NAME, SHARED_TOPIC),
                equalTo(MESSAGING_OPERATION, emitOldMessagingSemconv() ? "publish" : null),
                equalTo(MESSAGING_OPERATION_NAME, emitStableMessagingSemconv() ? "send" : null),
                equalTo(MESSAGING_OPERATION_TYPE, emitStableMessagingSemconv() ? "send" : null),
                satisfies(MESSAGING_DESTINATION_PARTITION_ID, AbstractStringAssert::isNotEmpty)));
    addClientIdAssertions(assertions, "producer");
    addOffsetAssertions(assertions);
    if (testHeaders) {
      assertions.add(
          equalTo(
              MessageHeaderUtil.headerAttributeKey("Test-Message-Header"), singletonList("test")));
    }
    if (testExperimental) {
      assertions.add(
          satisfies(
              stringKey("messaging.kafka.bootstrap.servers"),
              val -> val.matches("^localhost:\\d+(,localhost:\\d+)*$")));
    }
    return assertions;
  }

  static List<AttributeAssertion> processAttributes(
      String greeting, boolean testHeaders, boolean testExperimental) {
    List<AttributeAssertion> assertions =
        new ArrayList<>(
            asList(
                equalTo(MESSAGING_SYSTEM, "kafka"),
                equalTo(MESSAGING_DESTINATION_NAME, SHARED_TOPIC),
                equalTo(MESSAGING_OPERATION, emitOldMessagingSemconv() ? "process" : null),
                equalTo(MESSAGING_OPERATION_NAME, emitStableMessagingSemconv() ? "process" : null),
                equalTo(MESSAGING_OPERATION_TYPE, emitStableMessagingSemconv() ? "process" : null),
                equalTo(MESSAGING_MESSAGE_BODY_SIZE, greeting.getBytes(UTF_8).length),
                satisfies(MESSAGING_DESTINATION_PARTITION_ID, AbstractStringAssert::isNotEmpty),
                equalTo(MESSAGING_KAFKA_CONSUMER_GROUP, emitOldMessagingSemconv() ? "test" : null),
                equalTo(
                    MESSAGING_CONSUMER_GROUP_NAME, emitStableMessagingSemconv() ? "test" : null)));
    addClientIdAssertions(assertions, "consumer");
    addOffsetAssertions(assertions);
    if (testHeaders) {
      assertions.add(
          equalTo(
              MessageHeaderUtil.headerAttributeKey("Test-Message-Header"), singletonList("test")));
    }
    if (testExperimental) {
      assertions.add(
          satisfies(longKey("kafka.record.queue_time_ms"), AbstractLongAssert::isNotNegative));
    }
    return assertions;
  }

  protected static List<AttributeAssertion> receiveAttributes(boolean testHeaders) {
    List<AttributeAssertion> assertions =
        new ArrayList<>(
            asList(
                equalTo(MESSAGING_SYSTEM, "kafka"),
                equalTo(MESSAGING_DESTINATION_NAME, SHARED_TOPIC),
                equalTo(MESSAGING_OPERATION, emitOldMessagingSemconv() ? "receive" : null),
                equalTo(MESSAGING_OPERATION_NAME, emitStableMessagingSemconv() ? "poll" : null),
                equalTo(MESSAGING_OPERATION_TYPE, emitStableMessagingSemconv() ? "receive" : null),
                equalTo(MESSAGING_KAFKA_CONSUMER_GROUP, emitOldMessagingSemconv() ? "test" : null),
                equalTo(
                    MESSAGING_CONSUMER_GROUP_NAME, emitStableMessagingSemconv() ? "test" : null),
                equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 1)));
    addClientIdAssertions(assertions, "consumer");
    if (testHeaders) {
      assertions.add(
          equalTo(
              MessageHeaderUtil.headerAttributeKey("Test-Message-Header"), singletonList("test")));
    }
    return assertions;
  }

  private static void addClientIdAssertions(
      List<AttributeAssertion> assertions, String clientIdPrefix) {
    if (emitOldMessagingSemconv()) {
      assertions.add(satisfies(MESSAGING_CLIENT_ID_OLD, val -> val.startsWith(clientIdPrefix)));
    }
    if (emitStableMessagingSemconv()) {
      assertions.add(
          satisfies(stringKey("messaging.client.id"), val -> val.startsWith(clientIdPrefix)));
    }
  }

  private static void addOffsetAssertions(List<AttributeAssertion> assertions) {
    if (emitOldMessagingSemconv()) {
      assertions.add(satisfies(MESSAGING_KAFKA_MESSAGE_OFFSET, AbstractLongAssert::isNotNegative));
    }
    if (emitStableMessagingSemconv()) {
      assertions.add(satisfies(MESSAGING_KAFKA_OFFSET, AbstractLongAssert::isNotNegative));
    }
  }

  @Test
  void testConsumerError() {
    KafkaTelemetryBuilder telemetryBuilder = KafkaTelemetry.builder(testing.getOpenTelemetry());
    configure(telemetryBuilder);
    KafkaTelemetry telemetry = telemetryBuilder.build();

    Consumer<?, ?> mockConsumer = mock();
    IllegalStateException error = new IllegalStateException("test");
    when(mockConsumer.poll(Duration.ofSeconds(10))).thenThrow(error);
    Consumer<?, ?> wrappedConsumer = telemetry.wrap(mockConsumer);
    assertThatThrownBy(() -> wrappedConsumer.poll(Duration.ofSeconds(10))).isSameAs(error);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableMessagingSemconv() ? "poll" : "unknown receive")
                        .hasKind(emitStableMessagingSemconv() ? SpanKind.CLIENT : SpanKind.CONSUMER)
                        .hasNoParent()
                        .hasStatus(StatusData.error())
                        .hasException(emitExceptionAsSpanEvents() ? error : null)
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "kafka"),
                            equalTo(
                                MESSAGING_OPERATION, emitOldMessagingSemconv() ? "receive" : null),
                            equalTo(
                                MESSAGING_OPERATION_NAME,
                                emitStableMessagingSemconv() ? "poll" : null),
                            equalTo(
                                MESSAGING_OPERATION_TYPE,
                                emitStableMessagingSemconv() ? "receive" : null),
                            equalTo(
                                ERROR_TYPE,
                                emitStableMessagingSemconv()
                                    ? IllegalStateException.class.getName()
                                    : null),
                            equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 0))));

    if (emitExceptionAsLogs()) {
      testing.waitAndAssertLogRecords(
          logRecord ->
              logRecord
                  .hasSeverity(Severity.WARN)
                  .hasEventName("messaging.receive.exception")
                  .hasException(error)
                  .hasTotalAttributeCount(3));
    }
  }
}
