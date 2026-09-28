/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactor.v3_1;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ContextViewSelectorTest {

  private static final boolean CONTEXT_VIEW_ENABLED =
      Boolean.parseBoolean(System.getProperty("test.reactor.context-view.enabled", "true"));

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void contextViewBridgeSelectedByAgentConfiguration() {
    testing.runWithSpan(
        "parent",
        () -> {
          Context current = Context.current();
          reactor.util.context.Context reactorContext =
              ContextPropagationOperator.storeOpenTelemetryContext(
                  reactor.util.context.Context.empty(), current);

          if (CONTEXT_VIEW_ENABLED) {
            Context restored =
                ContextPropagationOperator.getOpenTelemetryContextFromContextView(
                    reactorContext, null);
            assertThat(restored).isNotNull();
            assertThat(Span.fromContext(restored).getSpanContext())
                .isEqualTo(Span.fromContext(current).getSpanContext());
          } else {
            assertThat(
                    ContextPropagationOperator.getOpenTelemetryContextFromContextView(
                        reactorContext, null))
                .isNull();
          }
        });

    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("parent")));
  }
}
