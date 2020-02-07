package io.opentelemetry.auto.decorator;

import io.opentelemetry.auto.instrumentation.api.MoreTags;
import io.opentelemetry.trace.Span;

public abstract class ClientDecorator extends BaseDecorator {

  protected abstract String service();

  @Override
  public Span afterStart(final Span span) {
    assert span != null;
    if (service() != null) {
      span.setAttribute(MoreTags.SERVICE_NAME, service());
    }
    return super.afterStart(span);
  }
}
