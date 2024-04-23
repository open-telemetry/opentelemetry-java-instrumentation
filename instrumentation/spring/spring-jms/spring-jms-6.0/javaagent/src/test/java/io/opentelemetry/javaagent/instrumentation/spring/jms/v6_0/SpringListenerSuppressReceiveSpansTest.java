/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.jms.v6_0;

import static io.opentelemetry.api.trace.SpanKind.CONSUMER;
import static io.opentelemetry.api.trace.SpanKind.PRODUCER;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;

import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import org.assertj.core.api.AbstractStringAssert;

class SpringListenerSuppressReceiveSpansTest extends AbstractSpringJmsListenerTest {

  @Override
  void assertSpringJmsListener() {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span ->
                    span.hasName("spring-jms-listener publish")
                        .hasKind(PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MessagingIncubatingAttributes.MESSAGING_SYSTEM, "jms"),
                            equalTo(
                                MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME,
                                "spring-jms-listener"),
                            equalTo(MessagingIncubatingAttributes.MESSAGING_OPERATION, "publish"),
                            satisfies(
                                MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID,
                                AbstractStringAssert::isNotBlank)),
                span ->
                    span.hasName("spring-jms-listener process")
                        .hasKind(CONSUMER)
                        .hasParent(trace.getSpan(1))
                        .hasTotalRecordedLinks(0)
                        .hasAttributesSatisfyingExactly(
                            equalTo(MessagingIncubatingAttributes.MESSAGING_SYSTEM, "jms"),
                            equalTo(
                                MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME,
                                "spring-jms-listener"),
                            equalTo(MessagingIncubatingAttributes.MESSAGING_OPERATION, "process"),
                            satisfies(
                                MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID,
                                AbstractStringAssert::isNotBlank)),
                span -> span.hasName("consumer").hasParent(trace.getSpan(2))));
  }
}
