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
import application.io.opentelemetry.api.trace.StatusCode;
import application.io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.AgentContextStorage;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ApplicationSpan implements Span {

  private final io.opentelemetry.api.trace.Span agentSpan;

  ApplicationSpan(io.opentelemetry.api.trace.Span agentSpan) {
    this.agentSpan = agentSpan;
  }

  io.opentelemetry.api.trace.Span getAgentSpan() {
    return agentSpan;
  }

  @Override
  public Span setAttribute(String key, String value) {
    agentSpan.setAttribute(key, value);
    return this;
  }

  @Override
  public Span setAttribute(String key, long value) {
    agentSpan.setAttribute(key, value);
    return this;
  }

  @Override
  public Span setAttribute(String key, double value) {
    agentSpan.setAttribute(key, value);
    return this;
  }

  @Override
  public Span setAttribute(String key, boolean value) {
    agentSpan.setAttribute(key, value);
    return this;
  }

  @Override
  public <T> Span setAttribute(AttributeKey<T> applicationKey, T value) {
    io.opentelemetry.api.common.AttributeKey<T> agentKey = Bridging.toAgent(applicationKey);
    if (agentKey != null) {
      agentSpan.setAttribute(agentKey, value);
    }
    return this;
  }

  @Override
  public Span addEvent(String name) {
    agentSpan.addEvent(name);
    return this;
  }

  @Override
  public Span addEvent(String name, long timestamp, TimeUnit unit) {
    agentSpan.addEvent(name, timestamp, unit);
    return this;
  }

  @Override
  public Span addEvent(String name, Attributes applicationAttributes) {
    agentSpan.addEvent(name, Bridging.toAgent(applicationAttributes));
    return this;
  }

  @Override
  public Span addEvent(
      String name, Attributes applicationAttributes, long timestamp, TimeUnit unit) {
    agentSpan.addEvent(name, Bridging.toAgent(applicationAttributes), timestamp, unit);
    return this;
  }

  @Override
  public Span setStatus(StatusCode status) {
    agentSpan.setStatus(Bridging.toAgent(status));
    return this;
  }

  @Override
  public Span setStatus(StatusCode status, String description) {
    agentSpan.setStatus(Bridging.toAgent(status), description);
    return this;
  }

  @Override
  public Span recordException(Throwable throwable) {
    agentSpan.recordException(throwable);
    return this;
  }

  @Override
  public Span recordException(Throwable throwable, Attributes attributes) {
    agentSpan.recordException(throwable, Bridging.toAgent(attributes));
    return this;
  }

  @Override
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
  public boolean equals(Object obj) {
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
    return "ApplicationSpan{" +
        "agentSpan=" + agentSpan +
        '}';
  }

  @Override
  public int hashCode() {
    return agentSpan.hashCode();
  }

  static class Builder implements SpanBuilder {

    private static final Logger logger = LoggerFactory.getLogger(Builder.class);

    private final io.opentelemetry.api.trace.SpanBuilder agentBuilder;

    Builder(io.opentelemetry.api.trace.SpanBuilder agentBuilder) {
      this.agentBuilder = agentBuilder;
    }

    @Override
    public SpanBuilder setParent(Context applicationContext) {
      agentBuilder.setParent(AgentContextStorage.getAgentContext(applicationContext));
      return this;
    }

    @Override
    public SpanBuilder setNoParent() {
      agentBuilder.setNoParent();
      return this;
    }

    @Override
    public SpanBuilder addLink(SpanContext applicationSpanContext) {
      agentBuilder.addLink(Bridging.toAgent(applicationSpanContext));
      return this;
    }

    @Override
    public SpanBuilder addLink(
        SpanContext applicationSpanContext, Attributes applicationAttributes) {
      agentBuilder.addLink(Bridging.toAgent(applicationSpanContext));
      return this;
    }

    @Override
    public SpanBuilder setAttribute(String key, String value) {
      agentBuilder.setAttribute(key, value);
      return this;
    }

    @Override
    public SpanBuilder setAttribute(String key, long value) {
      agentBuilder.setAttribute(key, value);
      return this;
    }

    @Override
    public SpanBuilder setAttribute(String key, double value) {
      agentBuilder.setAttribute(key, value);
      return this;
    }

    @Override
    public SpanBuilder setAttribute(String key, boolean value) {
      agentBuilder.setAttribute(key, value);
      return this;
    }

    @Override
    public <T> SpanBuilder setAttribute(AttributeKey<T> applicationKey, T value) {
      io.opentelemetry.api.common.AttributeKey<T> agentKey = Bridging.toAgent(applicationKey);
      if (agentKey != null) {
        agentBuilder.setAttribute(agentKey, value);
      }
      return this;
    }

    @Override
    public SpanBuilder setSpanKind(SpanKind applicationSpanKind) {
      io.opentelemetry.api.trace.SpanKind agentSpanKind = toAgentOrNull(applicationSpanKind);
      if (agentSpanKind != null) {
        agentBuilder.setSpanKind(agentSpanKind);
      }
      return this;
    }

    @Override
    public SpanBuilder setStartTimestamp(long startTimestamp, TimeUnit unit) {
      agentBuilder.setStartTimestamp(startTimestamp, unit);
      return this;
    }

    @Override
    public Span startSpan() {
      return new ApplicationSpan(agentBuilder.startSpan());
    }
  }
}
