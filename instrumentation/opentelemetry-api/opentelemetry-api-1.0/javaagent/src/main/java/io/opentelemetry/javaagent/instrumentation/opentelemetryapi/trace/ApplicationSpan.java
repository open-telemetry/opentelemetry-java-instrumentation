/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.ValueBridging;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

public class ApplicationSpan implements application.io.opentelemetry.api.trace.Span {

  private final Span agentSpan;

  public ApplicationSpan(Span agentSpan) {
    this.agentSpan = agentSpan;
  }

  Span getAgentSpan() {
    return agentSpan;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.trace.Span setAttribute(String key, String value) {
    agentSpan.setAttribute(key, value);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.trace.Span setAttribute(String key, long value) {
    agentSpan.setAttribute(key, value);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.trace.Span setAttribute(String key, double value) {
    agentSpan.setAttribute(key, value);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.trace.Span setAttribute(String key, boolean value) {
    agentSpan.setAttribute(key, value);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  // unchecked: toAgent returns raw AttributeKey, VALUE bridging requires casting to Object key
  @SuppressWarnings({"unchecked", "rawtypes"})
  public <T> application.io.opentelemetry.api.trace.Span setAttribute(
      application.io.opentelemetry.api.common.AttributeKey<T> applicationKey, T value) {
    AttributeKey<T> agentKey = Bridging.toAgent(applicationKey);
    if (agentKey != null) {
      // For VALUE type attributes, need to bridge the Value object as well
      if (applicationKey.getType().name().equals("VALUE")) {
        agentSpan.setAttribute((AttributeKey) agentKey, ValueBridging.toAgent(value));
      } else {
        agentSpan.setAttribute(agentKey, value);
      }
    }
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.trace.Span addEvent(String name) {
    agentSpan.addEvent(name);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.trace.Span addEvent(
      String name, long timestamp, TimeUnit unit) {
    agentSpan.addEvent(name, timestamp, unit);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.trace.Span addEvent(
      String name, application.io.opentelemetry.api.common.Attributes applicationAttributes) {
    agentSpan.addEvent(name, Bridging.toAgent(applicationAttributes));
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.trace.Span addEvent(
      String name,
      application.io.opentelemetry.api.common.Attributes applicationAttributes,
      long timestamp,
      TimeUnit unit) {
    agentSpan.addEvent(name, Bridging.toAgent(applicationAttributes), timestamp, unit);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.trace.Span addLink(
      application.io.opentelemetry.api.trace.SpanContext spanContext) {
    agentSpan.addLink(Bridging.toAgent(spanContext));
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.trace.Span addLink(
      application.io.opentelemetry.api.trace.SpanContext spanContext,
      application.io.opentelemetry.api.common.Attributes attributes) {
    agentSpan.addLink(Bridging.toAgent(spanContext), Bridging.toAgent(attributes));
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.trace.Span setStatus(
      application.io.opentelemetry.api.trace.StatusCode status) {
    agentSpan.setStatus(Bridging.toAgent(status));
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.trace.Span setStatus(
      application.io.opentelemetry.api.trace.StatusCode status, String description) {
    agentSpan.setStatus(Bridging.toAgent(status), description);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.trace.Span recordException(Throwable throwable) {
    agentSpan.recordException(throwable);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.trace.Span recordException(
      Throwable throwable, application.io.opentelemetry.api.common.Attributes attributes) {
    agentSpan.recordException(throwable, Bridging.toAgent(attributes));
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.trace.Span updateName(String name) {
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
  public application.io.opentelemetry.api.trace.SpanContext getSpanContext() {
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
