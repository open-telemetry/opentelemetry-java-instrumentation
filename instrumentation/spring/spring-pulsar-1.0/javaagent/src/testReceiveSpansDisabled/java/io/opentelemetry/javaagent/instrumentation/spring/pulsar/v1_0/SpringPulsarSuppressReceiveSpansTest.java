/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.pulsar.v1_0;

import static io.opentelemetry.api.trace.SpanKind.CONSUMER;
import static io.opentelemetry.api.trace.SpanKind.PRODUCER;

import io.opentelemetry.instrumentation.spring.pulsar.v1_0.AbstractSpringPulsarTest;

class SpringPulsarSuppressReceiveSpansTest extends AbstractSpringPulsarTest {

  @Override
  protected void assertSpringPulsar() {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span ->
                    span.hasName(OTEL_TOPIC + " publish")
                        .hasKind(PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(publishAttributes()),
                span ->
                    span.hasName(String.format("%s process", OTEL_TOPIC))
                        .hasKind(CONSUMER)
                        .hasParent(trace.getSpan(1))
                        .hasTotalRecordedLinks(0)
                        .hasAttributesSatisfyingExactly(processAttributes()),
                span -> span.hasName("consumer").hasParent(trace.getSpan(2))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName(String.format("%s receive", OTEL_TOPIC)).hasKind(CONSUMER)));
  }
}
