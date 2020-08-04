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

package io.opentelemetry.auto.instrumentation.opentelemetryapi.trace;

import static io.opentelemetry.auto.instrumentation.opentelemetryapi.trace.Bridging.toShaded;
import static io.opentelemetry.auto.instrumentation.opentelemetryapi.trace.Bridging.toShadedOrNull;

import io.opentelemetry.instrumentation.api.ContextStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import unshaded.io.grpc.Context;
import unshaded.io.opentelemetry.common.AttributeValue;
import unshaded.io.opentelemetry.common.Attributes;
import unshaded.io.opentelemetry.trace.EndSpanOptions;
import unshaded.io.opentelemetry.trace.Event;
import unshaded.io.opentelemetry.trace.Link;
import unshaded.io.opentelemetry.trace.Span;
import unshaded.io.opentelemetry.trace.SpanContext;
import unshaded.io.opentelemetry.trace.Status;

class UnshadedSpan implements Span {

  private final io.opentelemetry.trace.Span shadedSpan;

  UnshadedSpan(final io.opentelemetry.trace.Span shadedSpan) {
    this.shadedSpan = shadedSpan;
  }

  io.opentelemetry.trace.Span getShadedSpan() {
    return shadedSpan;
  }

  @Override
  public void setAttribute(final String key, final String value) {
    shadedSpan.setAttribute(key, value);
  }

  @Override
  public void setAttribute(final String key, final long value) {
    shadedSpan.setAttribute(key, value);
  }

  @Override
  public void setAttribute(final String key, final double value) {
    shadedSpan.setAttribute(key, value);
  }

  @Override
  public void setAttribute(final String key, final boolean value) {
    shadedSpan.setAttribute(key, value);
  }

  @Override
  public void setAttribute(final String key, final AttributeValue value) {
    io.opentelemetry.common.AttributeValue convertedValue = Bridging.toShadedOrNull(value);
    if (convertedValue != null) {
      shadedSpan.setAttribute(key, convertedValue);
    }
  }

  @Override
  public void addEvent(final String name) {
    shadedSpan.addEvent(name);
  }

  @Override
  public void addEvent(final String name, final long timestamp) {
    shadedSpan.addEvent(name, timestamp);
  }

  @Override
  public void addEvent(final String name, final Attributes attributes) {
    shadedSpan.addEvent(name, toShaded(attributes));
  }

  @Override
  public void addEvent(final String name, final Attributes attributes, final long timestamp) {
    shadedSpan.addEvent(name, toShaded(attributes), timestamp);
  }

  @Override
  public void addEvent(final Event event) {
    addEvent(event.getName(), event.getAttributes());
  }

  @Override
  public void addEvent(final Event event, final long timestamp) {
    addEvent(event.getName(), event.getAttributes(), timestamp);
  }

  @Override
  public void setStatus(final Status status) {
    io.opentelemetry.trace.Status shadedStatus = toShadedOrNull(status);
    if (shadedStatus != null) {
      shadedSpan.setStatus(shadedStatus);
    }
  }

  @Override
  public void recordException(Throwable throwable) {
    shadedSpan.recordException(throwable);
  }

  @Override
  public void updateName(final String name) {
    shadedSpan.updateName(name);
  }

  @Override
  public void end() {
    shadedSpan.end();
  }

  @Override
  public void end(final EndSpanOptions endOptions) {
    shadedSpan.end(toShaded(endOptions));
  }

  @Override
  public SpanContext getContext() {
    return Bridging.toUnshaded(shadedSpan.getContext());
  }

  @Override
  public boolean isRecording() {
    return shadedSpan.isRecording();
  }

  @Override
  public boolean equals(final Object other) {
    if (!(other instanceof UnshadedSpan)) {
      return false;
    }
    return shadedSpan.equals(((UnshadedSpan) other).shadedSpan);
  }

  static class Builder implements Span.Builder {

    private static final Logger log = LoggerFactory.getLogger(Builder.class);

    private final io.opentelemetry.trace.Span.Builder shadedBuilder;
    private final ContextStore<Context, io.grpc.Context> contextStore;

    Builder(
        final io.opentelemetry.trace.Span.Builder shadedBuilder,
        ContextStore<Context, io.grpc.Context> contextStore) {
      this.shadedBuilder = shadedBuilder;
      this.contextStore = contextStore;
    }

    @Override
    public Span.Builder setParent(final Span parent) {
      if (parent instanceof UnshadedSpan) {
        shadedBuilder.setParent(((UnshadedSpan) parent).getShadedSpan());
      } else {
        log.debug("unexpected parent span: {}", parent);
      }
      return this;
    }

    @Override
    public Span.Builder setParent(final SpanContext remoteParent) {
      shadedBuilder.setParent(toShaded(remoteParent));
      return this;
    }

    @Override
    public Span.Builder setParent(Context context) {
      shadedBuilder.setParent(contextStore.get(context));
      return this;
    }

    @Override
    public Span.Builder setNoParent() {
      shadedBuilder.setNoParent();
      return this;
    }

    @Override
    public Span.Builder addLink(final SpanContext spanContext) {
      shadedBuilder.addLink(toShaded(spanContext));
      return this;
    }

    @Override
    public Span.Builder addLink(final SpanContext spanContext, final Attributes attributes) {
      shadedBuilder.addLink(toShaded(spanContext));
      return this;
    }

    @Override
    public Span.Builder addLink(final Link link) {
      shadedBuilder.addLink(toShaded(link.getContext()), toShaded(link.getAttributes()));
      return this;
    }

    @Override
    public Span.Builder setAttribute(final String key, final String value) {
      shadedBuilder.setAttribute(key, value);
      return this;
    }

    @Override
    public Span.Builder setAttribute(final String key, final long value) {
      shadedBuilder.setAttribute(key, value);
      return this;
    }

    @Override
    public Span.Builder setAttribute(final String key, final double value) {
      shadedBuilder.setAttribute(key, value);
      return this;
    }

    @Override
    public Span.Builder setAttribute(final String key, final boolean value) {
      shadedBuilder.setAttribute(key, value);
      return this;
    }

    @Override
    public Span.Builder setAttribute(final String key, final AttributeValue value) {
      io.opentelemetry.common.AttributeValue convertedValue = Bridging.toShadedOrNull(value);
      if (convertedValue != null) {
        shadedBuilder.setAttribute(key, convertedValue);
      }
      return this;
    }

    @Override
    public Span.Builder setSpanKind(final Span.Kind spanKind) {
      io.opentelemetry.trace.Span.Kind shadedSpanKind = toShadedOrNull(spanKind);
      if (shadedSpanKind != null) {
        shadedBuilder.setSpanKind(shadedSpanKind);
      }
      return this;
    }

    @Override
    public Span.Builder setStartTimestamp(final long startTimestamp) {
      shadedBuilder.setStartTimestamp(startTimestamp);
      return this;
    }

    @Override
    public Span startSpan() {
      return new UnshadedSpan(shadedBuilder.startSpan());
    }
  }
}
