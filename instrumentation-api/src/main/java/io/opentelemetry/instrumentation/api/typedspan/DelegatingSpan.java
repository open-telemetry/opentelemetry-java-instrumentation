/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.typedspan;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.StatusCode;
import java.util.concurrent.TimeUnit;

public class DelegatingSpan implements Span {
  protected final Span delegate;

  public DelegatingSpan(Span delegate) {
    this.delegate = delegate;
  }

  @Override
  public Span setAttribute(String key, String value) {
    delegate.setAttribute(key, value);
    return this;
  }

  @Override
  public Span setAttribute(String key, long value) {
    delegate.setAttribute(key, value);
    return this;
  }

  @Override
  public Span setAttribute(String key, double value) {
    delegate.setAttribute(key, value);
    return this;
  }

  @Override
  public Span setAttribute(String key, boolean value) {
    delegate.setAttribute(key, value);
    return this;
  }

  @Override
  public <T> Span setAttribute(AttributeKey<T> attributeKey, T t) {
    delegate.setAttribute(attributeKey, t);
    return this;
  }

  @Override
  public Span addEvent(String name) {
    delegate.addEvent(name);
    return this;
  }

  @Override
  public Span addEvent(String name, long timestamp, TimeUnit unit) {
    delegate.addEvent(name, timestamp, unit);
    return this;
  }

  @Override
  public Span addEvent(String name, Attributes attributes) {
    delegate.addEvent(name, attributes);
    return this;
  }

  @Override
  public Span addEvent(String name, Attributes attributes, long timestamp, TimeUnit unit) {
    delegate.addEvent(name, attributes, timestamp, unit);
    return this;
  }

  @Override
  public Span setStatus(StatusCode status) {
    delegate.setStatus(status);
    return this;
  }

  @Override
  public Span setStatus(StatusCode status, String description) {
    delegate.setStatus(status, description);
    return this;
  }

  @Override
  public Span recordException(Throwable throwable) {
    delegate.recordException(throwable);
    return this;
  }

  @Override
  public Span recordException(Throwable throwable, Attributes attributes) {
    delegate.recordException(throwable, attributes);
    return this;
  }

  @Override
  public Span updateName(String name) {
    delegate.updateName(name);
    return this;
  }

  @Override
  public void end() {
    delegate.end();
  }

  @Override
  public void end(long timestamp, TimeUnit unit) {
    delegate.end(timestamp, unit);
  }

  @Override
  public SpanContext getSpanContext() {
    return delegate.getSpanContext();
  }

  @Override
  public boolean isRecording() {
    return delegate.isRecording();
  }
}
