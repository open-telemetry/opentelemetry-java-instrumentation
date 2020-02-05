package io.opentelemetry.auto.decorator;

import io.opentelemetry.trace.Span;

public abstract class ServerDecorator extends BaseDecorator {

  @Override
  public Span afterStart(final Span span) {
    assert span != null;
    return super.afterStart(span);
  }
}
