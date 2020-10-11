/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.typedspan;

import io.opentelemetry.common.AttributeKey;
import io.opentelemetry.common.Attributes;
import io.opentelemetry.trace.EndSpanOptions;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.StatusCanonicalCode;
import org.checkerframework.checker.nullness.qual.Nullable;

public class DelegatingSpan implements Span {
  protected final Span delegate;

  public DelegatingSpan(Span delegate) {
    this.delegate = delegate;
  }

  @Override
  public void setAttribute(String key, String value) {
    delegate.setAttribute(key, value);
  }

  @Override
  public void setAttribute(String key, long value) {
    delegate.setAttribute(key, value);
  }

  @Override
  public void setAttribute(String key, double value) {
    delegate.setAttribute(key, value);
  }

  @Override
  public void setAttribute(String key, boolean value) {
    delegate.setAttribute(key, value);
  }

  @Override
  public <T> void setAttribute(AttributeKey<T> attributeKey, T t) {
    delegate.setAttribute(attributeKey, t);
  }

  @Override
  public void addEvent(String name) {
    delegate.addEvent(name);
  }

  @Override
  public void addEvent(String name, long timestamp) {
    delegate.addEvent(name, timestamp);
  }

  @Override
  public void addEvent(String name, Attributes attributes) {
    delegate.addEvent(name, attributes);
  }

  @Override
  public void addEvent(String name, Attributes attributes, long timestamp) {
    delegate.addEvent(name, attributes, timestamp);
  }

  @Override
  public void setStatus(StatusCanonicalCode status) {
    delegate.setStatus(status);
  }

  @Override
  public void setStatus(StatusCanonicalCode status, @Nullable String description) {
    delegate.setStatus(status, description);
  }

  @Override
  public void recordException(Throwable throwable) {
    delegate.recordException(throwable);
  }

  @Override
  public void recordException(Throwable throwable, Attributes attributes) {
    delegate.recordException(throwable, attributes);
  }

  @Override
  public void updateName(String name) {
    delegate.updateName(name);
  }

  @Override
  public void end() {
    delegate.end();
  }

  @Override
  public void end(EndSpanOptions endOptions) {
    delegate.end(endOptions);
  }

  @Override
  public SpanContext getContext() {
    return delegate.getContext();
  }

  @Override
  public boolean isRecording() {
    return delegate.isRecording();
  }
}
