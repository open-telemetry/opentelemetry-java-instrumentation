/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.pulsar.v1_0;

import static io.opentelemetry.api.trace.SpanKind.CONSUMER;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.api.trace.SpanKind.PRODUCER;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanKind;

import io.opentelemetry.instrumentation.spring.pulsar.v1_0.AbstractSpringPulsarTest;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.concurrent.atomic.AtomicReference;

class SpringPulsarTest extends AbstractSpringPulsarTest {

  @Override
  protected void assertSpringPulsar() {
    AtomicReference<SpanData> producer = new AtomicReference<>();

    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(INTERNAL, CONSUMER),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span -> {
                  span.hasName(OTEL_TOPIC + " publish")
                      .hasKind(PRODUCER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(publishAttributes());

                  producer.set(trace.getSpan(1));
                }),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(String.format("%s receive", OTEL_TOPIC))
                        .hasKind(CONSUMER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(receiveAttributes()),
                span ->
                    span.hasName(String.format("%s process", OTEL_TOPIC))
                        .hasKind(CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasLinks(LinkData.create(producer.get().getSpanContext()))
                        .hasAttributesSatisfyingExactly(processAttributes()),
                span -> span.hasName("consumer").hasParent(trace.getSpan(1))));
  }
}
