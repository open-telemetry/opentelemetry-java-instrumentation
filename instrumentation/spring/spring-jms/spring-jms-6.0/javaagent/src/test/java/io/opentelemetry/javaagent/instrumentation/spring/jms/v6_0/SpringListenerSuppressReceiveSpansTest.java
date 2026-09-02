/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.jms.v6_0;

import static io.opentelemetry.api.trace.SpanKind.CONSUMER;
import static io.opentelemetry.api.trace.SpanKind.PRODUCER;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.instrumentation.testing.junit.MessagingMetricsAssertions.assertCounter;
import static io.opentelemetry.instrumentation.testing.junit.MessagingMetricsAssertions.assertHistogram;
import static io.opentelemetry.instrumentation.testing.junit.MessagingMetricsAssertions.assertNoMetric;
import static io.opentelemetry.instrumentation.testing.junit.MessagingMetricsAssertions.assertNoStableMetrics;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_SUBSCRIPTION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.trace.data.LinkData;
import org.assertj.core.api.AbstractStringAssert;

class SpringListenerSuppressReceiveSpansTest extends AbstractSpringJmsListenerTest {

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Override
  void assertSpringJmsListener() {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span ->
                    span.hasName(
                            emitStableMessagingSemconv()
                                ? "send spring-jms-listener"
                                : "spring-jms-listener publish")
                        .hasKind(PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "jms"),
                            equalTo(MESSAGING_DESTINATION_NAME, "spring-jms-listener"),
                            equalTo(
                                MESSAGING_OPERATION,
                                emitStableMessagingSemconv() ? null : "publish"),
                            equalTo(
                                MESSAGING_OPERATION_NAME,
                                emitStableMessagingSemconv() ? "send" : null),
                            equalTo(
                                MESSAGING_OPERATION_TYPE,
                                emitStableMessagingSemconv() ? "send" : null),
                            satisfies(MESSAGING_MESSAGE_ID, AbstractStringAssert::isNotBlank)),
                span -> {
                  span.hasName(
                          emitStableMessagingSemconv()
                              ? "process spring-jms-listener"
                              : "spring-jms-listener process")
                      .hasKind(CONSUMER)
                      .hasParent(trace.getSpan(1))
                      .hasAttributesSatisfyingExactly(
                          equalTo(MESSAGING_SYSTEM, "jms"),
                          equalTo(MESSAGING_DESTINATION_NAME, "spring-jms-listener"),
                          equalTo(
                              MESSAGING_OPERATION, emitStableMessagingSemconv() ? null : "process"),
                          equalTo(
                              MESSAGING_OPERATION_NAME,
                              emitStableMessagingSemconv() ? "process" : null),
                          equalTo(
                              MESSAGING_OPERATION_TYPE,
                              emitStableMessagingSemconv() ? "process" : null),
                          satisfies(MESSAGING_MESSAGE_ID, AbstractStringAssert::isNotBlank),
                          equalTo(
                              MESSAGING_DESTINATION_SUBSCRIPTION_NAME,
                              emitStableMessagingSemconv() ? "durable-subscription" : null));
                  if (emitStableMessagingSemconv()) {
                    span.hasLinks(LinkData.create(trace.getSpan(1).getSpanContext()));
                  } else {
                    span.hasTotalRecordedLinks(0);
                  }
                },
                span -> span.hasName("consumer").hasParent(trace.getSpan(2))));

    if (!emitStableMessagingSemconv()) {
      assertNoStableMetrics(testing, "io.opentelemetry.jms-3.0");
      assertNoStableMetrics(testing, "io.opentelemetry.spring-jms-6.0");
      return;
    }

    Attributes sendAttributes =
        Attributes.of(
            MESSAGING_OPERATION_NAME,
            "send",
            MESSAGING_SYSTEM,
            "jms",
            MESSAGING_DESTINATION_NAME,
            "spring-jms-listener");
    assertHistogram(
        testing,
        "io.opentelemetry.jms-3.0",
        "messaging.client.operation.duration",
        sendAttributes.toBuilder().put(MESSAGING_OPERATION_TYPE, "send").build());
    assertNoMetric(testing, "io.opentelemetry.jms-3.0", "messaging.client.consumed.messages");

    Attributes processAttributes =
        Attributes.builder()
            .put(MESSAGING_OPERATION_NAME, "process")
            .put(MESSAGING_SYSTEM, "jms")
            .put(MESSAGING_DESTINATION_NAME, "spring-jms-listener")
            .put(MESSAGING_DESTINATION_SUBSCRIPTION_NAME, "durable-subscription")
            .build();
    assertHistogram(
        testing,
        "io.opentelemetry.spring-jms-6.0",
        "messaging.process.duration",
        processAttributes);
    assertCounter(
        testing,
        "io.opentelemetry.spring-jms-6.0",
        "messaging.client.consumed.messages",
        1,
        processAttributes);
  }
}
