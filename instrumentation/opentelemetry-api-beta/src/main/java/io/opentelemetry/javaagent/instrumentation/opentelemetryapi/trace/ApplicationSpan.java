/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace;

import static io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging.toAgent;
import static io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging.toAgentOrNull;

import application.io.opentelemetry.common.AttributeKey;
import application.io.opentelemetry.common.Attributes;
import application.io.opentelemetry.context.Context;
import application.io.opentelemetry.trace.EndSpanOptions;
import application.io.opentelemetry.trace.Span;
import application.io.opentelemetry.trace.SpanContext;
import application.io.opentelemetry.trace.StatusCode;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ApplicationSpan implements Span {

  private final io.opentelemetry.trace.Span agentSpan;

  ApplicationSpan(io.opentelemetry.trace.Span agentSpan) {
    this.agentSpan = agentSpan;
  }

  io.opentelemetry.trace.Span getAgentSpan() {
    return agentSpan;
  }

  @Override
  public void setAttribute(String key, String value) {
    agentSpan.setAttribute(key, value);
  }

  @Override
  public void setAttribute(String key, long value) {
    agentSpan.setAttribute(key, value);
  }

  @Override
  public void setAttribute(String key, double value) {
    agentSpan.setAttribute(key, value);
  }

  @Override
  public void setAttribute(String key, boolean value) {
    agentSpan.setAttribute(key, value);
  }

  @Override
  public <T> void setAttribute(AttributeKey<T> applicationKey, T value) {
    io.opentelemetry.common.AttributeKey<T> agentKey = Bridging.toAgent(applicationKey);
    if (agentKey != null) {
      agentSpan.setAttribute(agentKey, value);
    }
  }

  @Override
  public void addEvent(String name) {
    agentSpan.addEvent(name);
  }

  @Override
  public void addEvent(String name, long timestamp) {
    agentSpan.addEvent(name, timestamp);
  }

  @Override
  public void addEvent(String name, Attributes applicationAttributes) {
    agentSpan.addEvent(name, Bridging.toAgent(applicationAttributes));
  }

  @Override
  public void addEvent(String name, Attributes applicationAttributes, long timestamp) {
    agentSpan.addEvent(name, Bridging.toAgent(applicationAttributes), timestamp);
  }

  @Override
  public void setStatus(StatusCode status) {
    agentSpan.setStatus(Bridging.toAgent(status));
  }

  @Override
  public void setStatus(StatusCode status, String description) {
    agentSpan.setStatus(Bridging.toAgent(status), description);
  }

  @Override
  public void recordException(Throwable throwable) {
    agentSpan.recordException(throwable);
  }

  @Override
  public void recordException(Throwable throwable, Attributes attributes) {
    agentSpan.recordException(throwable, Bridging.toAgent(attributes));
  }

  @Override
  public void updateName(String name) {
    agentSpan.updateName(name);
  }

  @Override
  public void end() {
    agentSpan.end();
  }

  @Override
  public void end(EndSpanOptions applicationEndOptions) {
    agentSpan.end(toAgent(applicationEndOptions));
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
  public boolean equals(Object other) {
    if (!(other instanceof ApplicationSpan)) {
      return false;
    }
    return agentSpan.equals(((ApplicationSpan) other).agentSpan);
  }

  static class Builder implements Span.Builder {

    private static final Logger log = LoggerFactory.getLogger(Builder.class);

    private final io.opentelemetry.trace.Span.Builder agentBuilder;
    private final ContextStore<Context, io.opentelemetry.context.Context> contextStore;

    Builder(
        io.opentelemetry.trace.Span.Builder agentBuilder,
        ContextStore<Context, io.opentelemetry.context.Context> contextStore) {
      this.agentBuilder = agentBuilder;
      this.contextStore = contextStore;
    }

    @Override
    public Span.Builder setParent(application.io.opentelemetry.context.Context applicationContext) {
      agentBuilder.setParent(contextStore.get(applicationContext));
      return this;
    }

    @Override
    public Span.Builder setNoParent() {
      agentBuilder.setNoParent();
      return this;
    }

    @Override
    public Span.Builder addLink(SpanContext applicationSpanContext) {
      agentBuilder.addLink(Bridging.toAgent(applicationSpanContext));
      return this;
    }

    @Override
    public Span.Builder addLink(
        SpanContext applicationSpanContext, Attributes applicationAttributes) {
      agentBuilder.addLink(Bridging.toAgent(applicationSpanContext));
      return this;
    }

    @Override
    public Span.Builder setAttribute(String key, String value) {
      agentBuilder.setAttribute(key, value);
      return this;
    }

    @Override
    public Span.Builder setAttribute(String key, long value) {
      agentBuilder.setAttribute(key, value);
      return this;
    }

    @Override
    public Span.Builder setAttribute(String key, double value) {
      agentBuilder.setAttribute(key, value);
      return this;
    }

    @Override
    public Span.Builder setAttribute(String key, boolean value) {
      agentBuilder.setAttribute(key, value);
      return this;
    }

    @Override
    public <T> Span.Builder setAttribute(AttributeKey<T> applicationKey, T value) {
      io.opentelemetry.common.AttributeKey<T> agentKey = Bridging.toAgent(applicationKey);
      if (agentKey != null) {
        agentBuilder.setAttribute(agentKey, value);
      }
      return this;
    }

    @Override
    public Span.Builder setSpanKind(Span.Kind applicationSpanKind) {
      io.opentelemetry.trace.Span.Kind agentSpanKind = toAgentOrNull(applicationSpanKind);
      if (agentSpanKind != null) {
        agentBuilder.setSpanKind(agentSpanKind);
      }
      return this;
    }

    @Override
    public Span.Builder setStartTimestamp(long startTimestamp) {
      agentBuilder.setStartTimestamp(startTimestamp);
      return this;
    }

    @Override
    public Span startSpan() {
      return new ApplicationSpan(agentBuilder.startSpan());
    }
  }
}
