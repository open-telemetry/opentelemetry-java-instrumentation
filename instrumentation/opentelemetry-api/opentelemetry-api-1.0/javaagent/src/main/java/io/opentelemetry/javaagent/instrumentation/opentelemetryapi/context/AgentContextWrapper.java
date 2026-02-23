/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.baggage.BaggageBridging;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class AgentContextWrapper implements application.io.opentelemetry.context.Context {

  static final List<ContextKeyBridge<?, ?>> CONTEXT_KEY_BRIDGES;

  static {
    List<ContextKeyBridge<?, ?>> bridges = new ArrayList<>();
    try {
      bridges.add(
          new ContextKeyBridge<application.io.opentelemetry.api.trace.Span, Span>(
              "application.io.opentelemetry.api.trace.SpanContextKey",
              "io.opentelemetry.api.trace.SpanContextKey",
              Bridging::toApplication,
              Bridging::toAgentOrNull));
    } catch (Throwable ignored) {
      // reflection error; in practice should never happen, we can ignore it
    }
    try {
      bridges.add(
          new ContextKeyBridge<>(
              "application.io.opentelemetry.api.baggage.BaggageContextKey",
              "io.opentelemetry.api.baggage.BaggageContextKey",
              BaggageBridging::toApplication,
              BaggageBridging::toAgent));
    } catch (Throwable ignored) {
      // reflection error; in practice should never happen, we can ignore it
    }
    bridges.addAll(InstrumentationApiContextBridging.instrumentationApiBridges());
    CONTEXT_KEY_BRIDGES = Collections.unmodifiableList(bridges);
  }

  final Context agentContext;
  final application.io.opentelemetry.context.Context applicationContext;

  AgentContextWrapper(Context agentContext) {
    this(agentContext, agentContext.get(AgentContextStorage.APPLICATION_CONTEXT));
  }

  AgentContextWrapper(
      Context agentContext, application.io.opentelemetry.context.Context applicationContext) {
    if (applicationContext instanceof AgentContextWrapper) {
      throw new IllegalStateException("Expected unwrapped context");
    }
    this.agentContext = agentContext;
    this.applicationContext = applicationContext;
  }

  Context toAgentContext() {
    if (agentContext.get(AgentContextStorage.APPLICATION_CONTEXT) == applicationContext) {
      return agentContext;
    }
    return agentContext.with(AgentContextStorage.APPLICATION_CONTEXT, applicationContext);
  }

  public Context getAgentContext() {
    return agentContext;
  }

  @Override
  public <V> V get(application.io.opentelemetry.context.ContextKey<V> key) {
    for (ContextKeyBridge<?, ?> bridge : CONTEXT_KEY_BRIDGES) {
      V value = bridge.get(this, key);
      if (value != null) {
        return value;
      }
    }

    return applicationContext.get(key);
  }

  @Override
  public <V> application.io.opentelemetry.context.Context with(
      application.io.opentelemetry.context.ContextKey<V> k1, V v1) {
    for (ContextKeyBridge<?, ?> bridge : CONTEXT_KEY_BRIDGES) {
      application.io.opentelemetry.context.Context context = bridge.with(this, k1, v1);
      if (context != null) {
        return context;
      }
    }
    return new AgentContextWrapper(agentContext, applicationContext.with(k1, v1));
  }

  @Override
  public String toString() {
    return "AgentContextWrapper{agentContext="
        + agentContext
        + ", applicationContext="
        + applicationContext
        + "}";
  }
}
