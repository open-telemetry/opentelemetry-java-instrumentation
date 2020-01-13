package io.opentelemetry.auto.decorator;

import io.opentelemetry.auto.api.MoreTags;
import io.opentelemetry.auto.instrumentation.api.AgentSpan;
import io.opentelemetry.auto.instrumentation.api.Tags;
import io.opentelemetry.trace.Span;

public abstract class ClientDecorator extends BaseDecorator {

  protected abstract String service();

  protected String spanKind() {
    return Tags.SPAN_KIND_CLIENT;
  }

  @Deprecated
  @Override
  public AgentSpan afterStart(final AgentSpan span) {
    afterStart(span.getSpan());
    return span;
  }

  @Override
  public Span afterStart(final Span span) {
    assert span != null;
    if (service() != null) {
      span.setAttribute(MoreTags.SERVICE_NAME, service());
    }
    span.setAttribute(Tags.SPAN_KIND, spanKind());
    return super.afterStart(span);
  }
}
