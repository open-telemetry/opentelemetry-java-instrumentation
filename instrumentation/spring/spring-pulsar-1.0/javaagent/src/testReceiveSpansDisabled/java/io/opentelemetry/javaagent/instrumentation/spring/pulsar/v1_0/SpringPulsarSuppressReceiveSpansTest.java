/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.pulsar.v1_0;

import static io.opentelemetry.api.trace.SpanKind.CONSUMER;
import static io.opentelemetry.api.trace.SpanKind.PRODUCER;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;

import io.opentelemetry.instrumentation.spring.pulsar.v1_0.AbstractSpringPulsarTest;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import io.opentelemetry.sdk.trace.data.LinkData;
import java.util.function.Consumer;

class SpringPulsarSuppressReceiveSpansTest extends AbstractSpringPulsarTest {

  @Override
  protected void assertSpringPulsar() {
    Consumer<TraceAssert> mainTrace =
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
                span -> span.hasName("consumer").hasParent(trace.getSpan(2)));

    if (!emitStableMessagingSemconv()) {
      // legacy mode is unchanged: a batch receive always emits a standalone "receive" span, even
      // when receive spans are disabled, because the legacy batch path never suppresses it
      testing.waitAndAssertTraces(
          mainTrace,
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName(OTEL_TOPIC + " receive").hasKind(CONSUMER)));
      return;
    }

    // stable/v3 with receive spans off: the internal listener poll produces no receive span, but
    // the pulsar client still records the receive metrics (including the consumed-messages count)
    testing.waitAndAssertTraces(mainTrace);
    assertStableProcessMetrics();
  }
}
