/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace;

import static io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging.toAgentOrNull;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.AgentContextStorage;
import java.util.concurrent.TimeUnit;

public class ApplicationSpanBuilder implements application.io.opentelemetry.api.trace.SpanBuilder {

  private final SpanBuilder agentBuilder;

  protected ApplicationSpanBuilder(SpanBuilder agentBuilder) {
    this.agentBuilder = agentBuilder;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.trace.SpanBuilder setParent(
      application.io.opentelemetry.context.Context applicationContext) {
    agentBuilder.setParent(AgentContextStorage.getAgentContext(applicationContext));
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.trace.SpanBuilder setNoParent() {
    agentBuilder.setNoParent();
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.trace.SpanBuilder addLink(
      application.io.opentelemetry.api.trace.SpanContext applicationSpanContext) {
    agentBuilder.addLink(Bridging.toAgent(applicationSpanContext));
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.trace.SpanBuilder addLink(
      application.io.opentelemetry.api.trace.SpanContext applicationSpanContext,
      application.io.opentelemetry.api.common.Attributes applicationAttributes) {
    agentBuilder.addLink(
        Bridging.toAgent(applicationSpanContext), Bridging.toAgent(applicationAttributes));
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.trace.SpanBuilder setAttribute(String key, String value) {
    agentBuilder.setAttribute(key, value);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.trace.SpanBuilder setAttribute(String key, long value) {
    agentBuilder.setAttribute(key, value);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.trace.SpanBuilder setAttribute(String key, double value) {
    agentBuilder.setAttribute(key, value);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.trace.SpanBuilder setAttribute(
      String key, boolean value) {
    agentBuilder.setAttribute(key, value);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public <T> application.io.opentelemetry.api.trace.SpanBuilder setAttribute(
      application.io.opentelemetry.api.common.AttributeKey<T> applicationKey, T value) {
    @SuppressWarnings("unchecked") // toAgent uses raw AttributeKey
    AttributeKey<T> agentKey = Bridging.toAgent(applicationKey);
    if (agentKey != null) {
      agentBuilder.setAttribute(agentKey, value);
    }
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.trace.SpanBuilder setSpanKind(
      application.io.opentelemetry.api.trace.SpanKind applicationSpanKind) {
    SpanKind agentSpanKind = toAgentOrNull(applicationSpanKind);
    if (agentSpanKind != null) {
      agentBuilder.setSpanKind(agentSpanKind);
    }
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.trace.SpanBuilder setStartTimestamp(
      long startTimestamp, TimeUnit unit) {
    agentBuilder.setStartTimestamp(startTimestamp, unit);
    return this;
  }

  @Override
  public application.io.opentelemetry.api.trace.Span startSpan() {
    return new ApplicationSpan(agentBuilder.startSpan());
  }
}
