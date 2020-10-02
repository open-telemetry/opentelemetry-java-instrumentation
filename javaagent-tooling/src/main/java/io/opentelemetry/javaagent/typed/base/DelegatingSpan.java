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

package io.opentelemetry.javaagent.typed.base;

import io.opentelemetry.common.AttributeKey;
import io.opentelemetry.common.Attributes;
import io.opentelemetry.trace.EndSpanOptions;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.Status;

// TODO: This should be moved into the API.
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
  public <T> void setAttribute(AttributeKey<T> key, T value) {
    delegate.setAttribute(key, value);
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
  public void setStatus(Status status) {
    delegate.setStatus(status);
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
