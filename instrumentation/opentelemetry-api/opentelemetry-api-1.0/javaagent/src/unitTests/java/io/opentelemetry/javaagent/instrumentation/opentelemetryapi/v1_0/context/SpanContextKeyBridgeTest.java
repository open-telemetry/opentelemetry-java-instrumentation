/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_0.context;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_0.trace.ApplicationSpan;
import org.junit.jupiter.api.Test;

class SpanContextKeyBridgeTest {

  private static final String TRACE_ID = "0123456789abcdef0123456789abcdef";

  private static Span agentSpan(String spanId) {
    return Span.wrap(
        SpanContext.create(TRACE_ID, spanId, TraceFlags.getSampled(), TraceState.getDefault()));
  }

  private static application.io.opentelemetry.context.Context root() {
    return new AgentContextWrapper(
        Context.root(), application.io.opentelemetry.context.Context.root());
  }

  @Test
  void returnsTheStoredSpan() {
    ApplicationSpan span = new ApplicationSpan(agentSpan("0123456789abcdef"));

    application.io.opentelemetry.context.Context context = root().with(span);

    assertThat(application.io.opentelemetry.api.trace.Span.fromContext(context)).isSameAs(span);
    assertThat(application.io.opentelemetry.api.trace.Span.fromContext(context)).isSameAs(span);
  }

  @Test
  void keepsTheStoredSpanWhenOtherValuesAreAdded() {
    ApplicationSpan span = new ApplicationSpan(agentSpan("0123456789abcdef"));
    application.io.opentelemetry.context.ContextKey<String> key =
        application.io.opentelemetry.context.ContextKey.named("test");

    application.io.opentelemetry.context.Context context = root().with(span).with(key, "value");

    assertThat(application.io.opentelemetry.api.trace.Span.fromContext(context)).isSameAs(span);
  }

  @Test
  void convertsASpanReplacedByAgentCode() {
    ApplicationSpan span = new ApplicationSpan(agentSpan("0123456789abcdef"));
    AgentContextWrapper context = (AgentContextWrapper) root().with(span);
    Span replacement = agentSpan("fedcba9876543210");

    application.io.opentelemetry.context.Context replaced =
        new AgentContextWrapper(context.agentContext.with(replacement), context.applicationContext);

    application.io.opentelemetry.api.trace.Span fromContext =
        application.io.opentelemetry.api.trace.Span.fromContext(replaced);
    assertThat(fromContext).isNotSameAs(span);
    assertThat(fromContext.getSpanContext().getSpanId()).isEqualTo("fedcba9876543210");
  }

  @Test
  void convertsASpanStoredByAgentCode() {
    application.io.opentelemetry.context.Context context =
        new AgentContextWrapper(
            Context.root().with(agentSpan("0123456789abcdef")),
            application.io.opentelemetry.context.Context.root());

    application.io.opentelemetry.api.trace.Span fromContext =
        application.io.opentelemetry.api.trace.Span.fromContext(context);
    assertThat(fromContext).isInstanceOf(ApplicationSpan.class);
    assertThat(fromContext.getSpanContext().getSpanId()).isEqualTo("0123456789abcdef");
  }
}
