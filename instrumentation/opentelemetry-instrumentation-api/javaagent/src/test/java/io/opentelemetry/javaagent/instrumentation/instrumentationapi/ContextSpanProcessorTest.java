/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.instrumentationapi;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.util.ContextSpanProcessorUtil;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ContextSpanProcessorTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void tesUpdateSpanName() {
    Context context = Context.current();
    context =
        ContextSpanProcessorUtil.storeInContext(
            context,
            (context1, span) -> {
              span.updateName("new span name");
            });

    try (Scope scope = context.makeCurrent()) {
      testing.runWithSpan("old span name", () -> {});
    }

    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("new span name")));
  }
}
