/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.auto.opentelemetryapi.trace;

import static io.opentelemetry.instrumentation.auto.opentelemetryapi.trace.Bridging.toAgent;
import static io.opentelemetry.instrumentation.auto.opentelemetryapi.trace.Bridging.toAgentOrNull;

import application.io.grpc.Context;
import application.io.opentelemetry.common.AttributeValue;
import application.io.opentelemetry.common.Attributes;
import application.io.opentelemetry.trace.EndSpanOptions;
import application.io.opentelemetry.trace.Event;
import application.io.opentelemetry.trace.Link;
import application.io.opentelemetry.trace.Span;
import application.io.opentelemetry.trace.SpanContext;
import application.io.opentelemetry.trace.Status;
import io.opentelemetry.instrumentation.auto.api.ContextStore;
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
  public void setAttribute(String key, AttributeValue applicationValue) {
    io.opentelemetry.common.AttributeValue agentValue = Bridging.toAgentOrNull(applicationValue);
    if (agentValue != null) {
      agentSpan.setAttribute(key, agentValue);
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
  public void addEvent(
      String name, Attributes applicationAttributes, long timestamp) {
    agentSpan.addEvent(name, Bridging.toAgent(applicationAttributes), timestamp);
  }

  @Override
  public void addEvent(Event applicationEvent) {
    addEvent(applicationEvent.getName(), applicationEvent.getAttributes());
  }

  @Override
  public void addEvent(Event applicationEvent, long timestamp) {
    addEvent(applicationEvent.getName(), applicationEvent.getAttributes(), timestamp);
  }

  @Override
  public void setStatus(Status applicationStatus) {
    io.opentelemetry.trace.Status agentStatus = Bridging.toAgentOrNull(applicationStatus);
    if (agentStatus != null) {
      agentSpan.setStatus(agentStatus);
    }
  }

  @Override
  public void recordException(Throwable throwable) {
    agentSpan.recordException(throwable);
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
  public SpanContext getContext() {
    return Bridging.toApplication(agentSpan.getContext());
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
    private final ContextStore<Context, io.grpc.Context> contextStore;

    Builder(
        io.opentelemetry.trace.Span.Builder agentBuilder,
        ContextStore<Context, io.grpc.Context> contextStore) {
      this.agentBuilder = agentBuilder;
      this.contextStore = contextStore;
    }

    @Override
    public Span.Builder setParent(Span applicationParent) {
      if (applicationParent instanceof ApplicationSpan) {
        agentBuilder.setParent(((ApplicationSpan) applicationParent).getAgentSpan());
      } else {
        log.debug("unexpected parent span: {}", applicationParent);
      }
      return this;
    }

    @Override
    public Span.Builder setParent(SpanContext applicationRemoteParent) {
      agentBuilder.setParent(Bridging.toAgent(applicationRemoteParent));
      return this;
    }

    @Override
    public Span.Builder setParent(Context applicationContext) {
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
    public Span.Builder addLink(Link applicationLink) {
      agentBuilder.addLink(
          Bridging.toAgent(applicationLink.getContext()),
          Bridging.toAgent(applicationLink.getAttributes()));
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
    public Span.Builder setAttribute(String key, AttributeValue applicationValue) {
      io.opentelemetry.common.AttributeValue agentValue = Bridging.toAgentOrNull(applicationValue);
      if (agentValue != null) {
        agentBuilder.setAttribute(key, agentValue);
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
