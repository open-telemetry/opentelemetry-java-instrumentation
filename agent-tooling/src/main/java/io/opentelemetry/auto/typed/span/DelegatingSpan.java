package io.opentelemetry.auto.typed.span;

import io.opentelemetry.trace.AttributeValue;
import io.opentelemetry.trace.EndSpanOptions;
import io.opentelemetry.trace.Event;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.Status;

import java.util.Map;

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
  public void setAttribute(String key, AttributeValue value) {
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
  public void addEvent(String name, Map<String, AttributeValue> attributes) {
    delegate.addEvent(name, attributes);
  }

  @Override
  public void addEvent(String name, Map<String, AttributeValue> attributes, long timestamp) {
    delegate.addEvent(name, attributes, timestamp);
  }

  @Override
  public void addEvent(Event event) {
    delegate.addEvent(event);
  }

  @Override
  public void addEvent(Event event, long timestamp) {
    delegate.addEvent(event, timestamp);
  }

  @Override
  public void setStatus(Status status) {
    delegate.setStatus(status);
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
