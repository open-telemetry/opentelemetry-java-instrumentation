/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class DefaultEnablementTest {

  private static final boolean INSTRUMENTATION_ENABLED =
      Boolean.parseBoolean(
          System.getProperty("otel.instrumentation.opentelemetry-api.enabled", "true"));

  @RegisterExtension
  private static final AgentInstrumentationExtension testing =
      AgentInstrumentationExtension.create();

  @Test
  void defaultEnablement() {
    bridgedSpan();

    if (INSTRUMENTATION_ENABLED) {
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("parent").hasNoParent(),
                  span -> span.hasName("bridged").hasParent(trace.getSpan(0))));
    } else {
      testing.waitAndAssertTraces(
          trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("parent").hasNoParent()));
    }
  }

  // The annotation supplies a completion span even when the application API bridge is disabled.
  @WithSpan("parent")
  private static void bridgedSpan() {
    Span span = GlobalOpenTelemetry.getTracer("test").spanBuilder("bridged").startSpan();
    span.end();
  }
}
