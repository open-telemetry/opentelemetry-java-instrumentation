/*
 * Copyright 2020, OpenTelemetry Authors
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
package io.opentelemetry.auto.instrumentation.opentelemetryapi;

import static io.opentelemetry.auto.instrumentation.opentelemetryapi.Bridging.toShaded;
import static io.opentelemetry.auto.instrumentation.opentelemetryapi.Bridging.toShadedOrNull;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import unshaded.io.opentelemetry.common.AttributeValue;
import unshaded.io.opentelemetry.trace.Link;
import unshaded.io.opentelemetry.trace.Span;
import unshaded.io.opentelemetry.trace.SpanContext;

@Slf4j
public class UnshadedSpanBuilder implements Span.Builder {

  private final io.opentelemetry.trace.Span.Builder shadedBuilder;

  public UnshadedSpanBuilder(final io.opentelemetry.trace.Span.Builder shadedBuilder) {
    this.shadedBuilder = shadedBuilder;
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
  public Span.Builder addLink(
      final SpanContext spanContext, final Map<String, AttributeValue> attributes) {
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
    final io.opentelemetry.common.AttributeValue convertedValue = Bridging.toShadedOrNull(value);
    if (convertedValue != null) {
      shadedBuilder.setAttribute(key, convertedValue);
    }
    return this;
  }

  @Override
  public Span.Builder setSpanKind(final Span.Kind spanKind) {
    final io.opentelemetry.trace.Span.Kind shadedSpanKind = toShadedOrNull(spanKind);
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
