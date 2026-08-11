/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.pulsar.v1_0;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.CONSUMER;
import static io.opentelemetry.api.trace.SpanKind.PRODUCER;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;

import io.opentelemetry.instrumentation.spring.pulsar.v1_0.AbstractSpringPulsarTest;
import io.opentelemetry.sdk.trace.data.LinkData;

class SpringPulsarSuppressReceiveSpansTest extends AbstractSpringPulsarTest {

  @Override
  protected void assertSpringPulsar() {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span ->
                    span.hasName(
                            emitStableMessagingSemconv()
                                ? "send " + OTEL_TOPIC
                                : OTEL_TOPIC + " publish")
                        .hasKind(PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(publishAttributes()),
                span -> {
                  span.hasName(
                          emitStableMessagingSemconv()
                              ? "process " + OTEL_TOPIC
                              : OTEL_TOPIC + " process")
                      .hasKind(CONSUMER)
                      .hasParent(trace.getSpan(1))
                      .hasAttributesSatisfyingExactly(processAttributes());
                  if (emitStableMessagingSemconv()) {
                    span.hasLinks(LinkData.create(trace.getSpan(1).getSpanContext()));
                  } else {
                    span.hasTotalRecordedLinks(0);
                  }
                },
                span -> span.hasName("consumer").hasParent(trace.getSpan(2))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(
                            emitStableMessagingSemconv()
                                ? "receive " + OTEL_TOPIC
                                : OTEL_TOPIC + " receive")
                        .hasKind(emitStableMessagingSemconv() ? CLIENT : CONSUMER)));
    assertStableProcessMetrics();
  }
}
