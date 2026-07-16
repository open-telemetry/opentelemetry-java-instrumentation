/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.v2_6;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldMessagingSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
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

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.AbstractLongAssert;
import org.assertj.core.api.AbstractStringAssert;

@SuppressWarnings("deprecation") // using deprecated semconv
class InterceptorsSuppressReceiveSpansTest extends AbstractInterceptorsTest {

  private static final KafkaTelemetry kafkaTelemetry =
      KafkaTelemetry.create(testing.getOpenTelemetry());

  @Override
  protected KafkaTelemetry kafkaTelemetry() {
    return kafkaTelemetry;
  }

  @Override
  protected boolean captureExperimentalSpanAttributes() {
    return false;
  }

  @Override
  void assertTraces() {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName(
                            emitStableMessagingSemconv()
                                ? "send " + SHARED_TOPIC
                                : SHARED_TOPIC + " publish")
                        .hasKind(SpanKind.PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(sendAttributes()),
                span ->
                    span.hasName(
                            emitStableMessagingSemconv()
                                ? "process " + SHARED_TOPIC
                                : SHARED_TOPIC + " process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(processAttributes()),
                span ->
                    span.hasName("process child")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(2))),
        // ideally we'd want producer callback to be part of the main trace, we just aren't able to
        // instrument that
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("producer callback").hasKind(SpanKind.INTERNAL).hasNoParent()));
  }

  private static List<AttributeAssertion> sendAttributes() {
    List<AttributeAssertion> assertions =
        new ArrayList<>(
            asList(
                equalTo(MESSAGING_SYSTEM, "kafka"),
                equalTo(MESSAGING_DESTINATION_NAME, SHARED_TOPIC),
                equalTo(MESSAGING_OPERATION, emitOldMessagingSemconv() ? "publish" : null),
                equalTo(MESSAGING_OPERATION_NAME, emitStableMessagingSemconv() ? "send" : null),
                equalTo(MESSAGING_OPERATION_TYPE, emitStableMessagingSemconv() ? "send" : null)));
    addClientIdAssertion(assertions, "producer");
    return assertions;
  }

  private static List<AttributeAssertion> processAttributes() {
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
                    MESSAGING_CONSUMER_GROUP_NAME, emitStableMessagingSemconv() ? "test" : null),
                equalTo(stringKey("test-baggage-key-1"), "test-baggage-value-1"),
                equalTo(stringKey("test-baggage-key-2"), "test-baggage-value-2")));
    if (emitOldMessagingSemconv()) {
      assertions.add(satisfies(MESSAGING_KAFKA_MESSAGE_OFFSET, AbstractLongAssert::isNotNegative));
    }
    if (emitStableMessagingSemconv()) {
      assertions.add(satisfies(MESSAGING_KAFKA_OFFSET, AbstractLongAssert::isNotNegative));
    }
    addClientIdAssertion(assertions, "consumer");
    return assertions;
  }

  private static void addClientIdAssertion(
      List<AttributeAssertion> assertions, String clientIdPrefix) {
    if (emitOldMessagingSemconv()) {
      assertions.add(satisfies(MESSAGING_CLIENT_ID_OLD, val -> val.startsWith(clientIdPrefix)));
    }
    if (emitStableMessagingSemconv()) {
      assertions.add(
          satisfies(stringKey("messaging.client.id"), val -> val.startsWith(clientIdPrefix)));
    }
  }
}
