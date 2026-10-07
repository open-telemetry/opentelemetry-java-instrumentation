/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.pulsar.v1_0;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.CONSUMER;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.api.trace.SpanKind.PRODUCER;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanKind;

import io.opentelemetry.instrumentation.spring.pulsar.v1_0.AbstractSpringPulsarTest;
import io.opentelemetry.sdk.trace.data.LinkData;

class SpringPulsarTest extends AbstractSpringPulsarTest {

  @Override
  protected void assertSpringPulsar() {
    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(INTERNAL, CLIENT),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span ->
                    span.hasName("send " + OTEL_TOPIC)
                        .hasKind(PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(publishAttributes()),
                span ->
                    span.hasName("process " + OTEL_TOPIC)
                        .hasKind(CONSUMER)
                        .hasParent(trace.getSpan(1))
                        .hasLinks(LinkData.create(trace.getSpan(1).getSpanContext()))
                        .hasAttributesSatisfyingExactly(processAttributes()),
                span -> span.hasName("consumer").hasParent(trace.getSpan(2))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("receive " + OTEL_TOPIC)
                        .hasKind(CLIENT)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(receiveAttributes())));
    assertProcessMetrics();
  }
}
