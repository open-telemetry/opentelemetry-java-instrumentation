/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactor.v3_4;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.attributeEntry;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ContextPropagationOperator34InstrumentationTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void storeAndGetContext() {
    reactor.util.context.Context reactorContext = reactor.util.context.Context.empty();
    testing.runWithSpan(
        "parent",
        () -> {
          reactor.util.context.Context newReactorContext =
              ContextPropagationOperator.storeOpenTelemetryContext(
                  reactorContext, Context.current());
          Context otelContext =
              ContextPropagationOperator.getOpenTelemetryContextFromContextView(
                  newReactorContext, null);
          assertThat(otelContext).isNotNull();
          Span.fromContext(otelContext).setAttribute("foo", "bar");
          Context otelContext2 =
              ContextPropagationOperator.getOpenTelemetryContext(newReactorContext, null);
          assertThat(otelContext2).isNotNull();
          Span.fromContext(otelContext2).setAttribute("foo2", "bar2");
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("parent")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasAttributes(
                            attributeEntry("foo", "bar"), attributeEntry("foo2", "bar2"))));
  }
}
