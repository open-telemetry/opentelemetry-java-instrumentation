/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace;

import static io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging.toAgentOrNull;

import application.io.opentelemetry.api.common.AttributeKey;
import application.io.opentelemetry.api.common.Attributes;
import application.io.opentelemetry.api.trace.Span;
import application.io.opentelemetry.api.trace.SpanBuilder;
import application.io.opentelemetry.api.trace.SpanContext;
import application.io.opentelemetry.api.trace.SpanKind;
import application.io.opentelemetry.context.Context;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.AgentContextStorage;
import java.util.concurrent.TimeUnit;

public class ApplicationSpanBuilder implements SpanBuilder {

  private final io.opentelemetry.api.trace.SpanBuilder agentBuilder;

  protected ApplicationSpanBuilder(io.opentelemetry.api.trace.SpanBuilder agentBuilder) {
    this.agentBuilder = agentBuilder;
  }

  @Override
  @CanIgnoreReturnValue
  public SpanBuilder setParent(Context applicationContext) {
    agentBuilder.setParent(AgentContextStorage.getAgentContext(applicationContext));
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public SpanBuilder setNoParent() {
    agentBuilder.setNoParent();
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public SpanBuilder addLink(SpanContext applicationSpanContext) {
    agentBuilder.addLink(Bridging.toAgent(applicationSpanContext));
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public SpanBuilder addLink(SpanContext applicationSpanContext, Attributes applicationAttributes) {
    agentBuilder.addLink(Bridging.toAgent(applicationSpanContext));
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public SpanBuilder setAttribute(String key, String value) {
    agentBuilder.setAttribute(key, value);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public SpanBuilder setAttribute(String key, long value) {
    agentBuilder.setAttribute(key, value);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public SpanBuilder setAttribute(String key, double value) {
    agentBuilder.setAttribute(key, value);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public SpanBuilder setAttribute(String key, boolean value) {
    agentBuilder.setAttribute(key, value);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public <T> SpanBuilder setAttribute(AttributeKey<T> applicationKey, T value) {
    @SuppressWarnings("unchecked")
    io.opentelemetry.api.common.AttributeKey<T> agentKey = Bridging.toAgent(applicationKey);
    if (agentKey != null) {
      agentBuilder.setAttribute(agentKey, value);
    }
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public SpanBuilder setSpanKind(SpanKind applicationSpanKind) {
    io.opentelemetry.api.trace.SpanKind agentSpanKind = toAgentOrNull(applicationSpanKind);
    if (agentSpanKind != null) {
      agentBuilder.setSpanKind(agentSpanKind);
    }
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public SpanBuilder setStartTimestamp(long startTimestamp, TimeUnit unit) {
    agentBuilder.setStartTimestamp(startTimestamp, unit);
    return this;
  }

  @Override
  public Span startSpan() {
    return new ApplicationSpan(agentBuilder.startSpan());
  }
}
