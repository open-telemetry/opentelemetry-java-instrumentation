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
import unshaded.io.opentelemetry.common.AttributeValue;
import unshaded.io.opentelemetry.trace.EndSpanOptions;
import unshaded.io.opentelemetry.trace.Event;
import unshaded.io.opentelemetry.trace.Span;
import unshaded.io.opentelemetry.trace.SpanContext;
import unshaded.io.opentelemetry.trace.Status;

public class UnshadedSpan implements Span {

  private final io.opentelemetry.trace.Span shadedSpan;

  public UnshadedSpan(final io.opentelemetry.trace.Span shadedSpan) {
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
    final io.opentelemetry.common.AttributeValue convertedValue = Bridging.toShadedOrNull(value);
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
  public void addEvent(final String name, final Map<String, AttributeValue> attributes) {
    shadedSpan.addEvent(name, toShaded(attributes));
  }

  @Override
  public void addEvent(
      final String name, final Map<String, AttributeValue> attributes, final long timestamp) {
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
    final io.opentelemetry.trace.Status shadedStatus = toShadedOrNull(status);
    if (shadedStatus != null) {
      shadedSpan.setStatus(shadedStatus);
    }
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
}
