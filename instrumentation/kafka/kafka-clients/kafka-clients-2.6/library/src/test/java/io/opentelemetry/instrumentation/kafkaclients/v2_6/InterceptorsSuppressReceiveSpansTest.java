/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.v2_6;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_CLIENT_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_CONSUMER_GROUP_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_KAFKA_CLUSTER_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_KAFKA_OFFSET;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static java.util.Arrays.asList;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.AbstractLongAssert;
import org.assertj.core.api.AbstractStringAssert;

class InterceptorsSuppressReceiveSpansTest extends AbstractInterceptorsTest {

  private static final KafkaTelemetry kafkaTelemetry =
      KafkaTelemetry.builder(testing.getOpenTelemetry())
          .setMessagingReceiveTelemetryEnabled(false)
          .build();

  @Override
  protected KafkaTelemetry kafkaTelemetry() {
    return kafkaTelemetry;
  }

  @Override
  protected boolean captureExperimentalSpanAttributes() {
    return false;
  }

  @Override
  protected boolean receiveTelemetryEnabled() {
    return false;
  }

  @Override
  void assertTraces() {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("send " + SHARED_TOPIC)
                        .hasKind(SpanKind.PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(sendAttributes()),
                span ->
                    span.hasName("process " + SHARED_TOPIC)
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
                equalTo(MESSAGING_OPERATION_NAME, "send"),
                equalTo(MESSAGING_OPERATION_TYPE, "send")));
    assertions.add(satisfies(MESSAGING_CLIENT_ID, val -> val.startsWith("producer")));
    assertions.add(satisfies(MESSAGING_KAFKA_CLUSTER_ID, AbstractStringAssert::isNotEmpty));
    return assertions;
  }

  private static List<AttributeAssertion> processAttributes() {
    List<AttributeAssertion> assertions =
        new ArrayList<>(
            asList(
                equalTo(MESSAGING_SYSTEM, "kafka"),
                equalTo(MESSAGING_DESTINATION_NAME, SHARED_TOPIC),
                equalTo(MESSAGING_OPERATION_NAME, "process"),
                equalTo(MESSAGING_OPERATION_TYPE, "process"),
                satisfies(MESSAGING_DESTINATION_PARTITION_ID, AbstractStringAssert::isNotEmpty),
                equalTo(MESSAGING_CONSUMER_GROUP_NAME, "test"),
                equalTo(stringKey("test-baggage-key-1"), "test-baggage-value-1"),
                equalTo(stringKey("test-baggage-key-2"), "test-baggage-value-2")));

    assertions.add(satisfies(MESSAGING_KAFKA_OFFSET, AbstractLongAssert::isNotNegative));
    assertions.add(satisfies(MESSAGING_CLIENT_ID, val -> val.startsWith("consumer")));
    assertions.add(satisfies(MESSAGING_KAFKA_CLUSTER_ID, AbstractStringAssert::isNotEmpty));
    return assertions;
  }
}
