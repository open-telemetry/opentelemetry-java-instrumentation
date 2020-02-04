package io.opentelemetry.auto.decorator;

import io.opentelemetry.auto.instrumentation.api.Tags;
import io.opentelemetry.trace.Span;

public abstract class ServerDecorator extends BaseDecorator {

  @Override
  public Span afterStart(final Span span) {
    assert span != null;
    span.setAttribute(Tags.SPAN_KIND, Tags.SPAN_KIND_SERVER);
    return super.afterStart(span);
  }
}
