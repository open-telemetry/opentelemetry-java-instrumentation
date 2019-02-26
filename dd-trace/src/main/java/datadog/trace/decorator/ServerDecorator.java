package datadog.trace.decorator;

import datadog.trace.tracer.Span;

public abstract class ServerDecorator extends BaseDecorator {

  protected abstract String spanType();

  @Override
  public Span afterStart(final Span span) {
    assert span != null;
    span.setMeta("span.kind", "server");
    span.setMeta("span.type", spanType());
    return super.afterStart(span);
  }
}
