package datadog.trace.decorator;

import datadog.trace.tracer.Span;

public abstract class ClientDecorator extends BaseDecorator {

  protected abstract String service();

  protected abstract String spanType();

  @Override
  public Span afterStart(final Span span) {
    assert span != null;
    if (service() != null) {
      span.setService(service());
    }
    span.setMeta("span.kind", "client");
    span.setMeta("span.type", spanType());
    return super.afterStart(span);
  }
}
