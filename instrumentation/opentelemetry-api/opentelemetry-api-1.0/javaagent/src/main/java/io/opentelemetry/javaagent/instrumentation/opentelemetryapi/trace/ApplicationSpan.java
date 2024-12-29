/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace;

import application.io.opentelemetry.api.common.AttributeKey;
import application.io.opentelemetry.api.common.Attributes;
import application.io.opentelemetry.api.trace.Span;
import application.io.opentelemetry.api.trace.SpanContext;
import application.io.opentelemetry.api.trace.StatusCode;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

public class ApplicationSpan implements Span {

  private final io.opentelemetry.api.trace.Span agentSpan;

  public ApplicationSpan(io.opentelemetry.api.trace.Span agentSpan) {
    this.agentSpan = agentSpan;
  }

  io.opentelemetry.api.trace.Span getAgentSpan() {
    return agentSpan;
  }

  @Override
  @CanIgnoreReturnValue
  public Span setAttribute(String key, String value) {
    agentSpan.setAttribute(key, value);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public Span setAttribute(String key, long value) {
    agentSpan.setAttribute(key, value);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public Span setAttribute(String key, double value) {
    agentSpan.setAttribute(key, value);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public Span setAttribute(String key, boolean value) {
    agentSpan.setAttribute(key, value);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public <T> Span setAttribute(AttributeKey<T> applicationKey, T value) {
    @SuppressWarnings("unchecked")
    io.opentelemetry.api.common.AttributeKey<T> agentKey = Bridging.toAgent(applicationKey);
    if (agentKey != null) {
      agentSpan.setAttribute(agentKey, value);
    }
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public Span addEvent(String name) {
    agentSpan.addEvent(name);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public Span addEvent(String name, long timestamp, TimeUnit unit) {
    agentSpan.addEvent(name, timestamp, unit);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public Span addEvent(String name, Attributes applicationAttributes) {
    agentSpan.addEvent(name, Bridging.toAgent(applicationAttributes));
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public Span addEvent(
      String name, Attributes applicationAttributes, long timestamp, TimeUnit unit) {
    agentSpan.addEvent(name, Bridging.toAgent(applicationAttributes), timestamp, unit);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public Span addLink(SpanContext spanContext) {
    agentSpan.addLink(Bridging.toAgent(spanContext));
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public Span addLink(SpanContext spanContext, Attributes attributes) {
    agentSpan.addLink(Bridging.toAgent(spanContext), Bridging.toAgent(attributes));
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public Span setStatus(StatusCode status) {
    agentSpan.setStatus(Bridging.toAgent(status));
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public Span setStatus(StatusCode status, String description) {
    agentSpan.setStatus(Bridging.toAgent(status), description);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public Span recordException(Throwable throwable) {
    agentSpan.recordException(throwable);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public Span recordException(Throwable throwable, Attributes attributes) {
    agentSpan.recordException(throwable, Bridging.toAgent(attributes));
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public Span updateName(String name) {
    agentSpan.updateName(name);
    return this;
  }

  @Override
  public void end() {
    agentSpan.end();
  }

  @Override
  public void end(long timestamp, TimeUnit unit) {
    agentSpan.end(timestamp, unit);
  }

  @Override
  public SpanContext getSpanContext() {
    return Bridging.toApplication(agentSpan.getSpanContext());
  }

  @Override
  public boolean isRecording() {
    return agentSpan.isRecording();
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof ApplicationSpan)) {
      return false;
    }
    ApplicationSpan other = (ApplicationSpan) obj;
    return agentSpan.equals(other.agentSpan);
  }

  @Override
  public String toString() {
    return "ApplicationSpan{agentSpan=" + agentSpan + '}';
  }

  @Override
  public int hashCode() {
    return agentSpan.hashCode();
  }
}
