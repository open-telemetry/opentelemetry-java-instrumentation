package io.opentelemetry.auto.decorator;

import io.opentelemetry.auto.instrumentation.api.AgentSpan;
import io.opentelemetry.auto.instrumentation.api.Tags;

public abstract class ServerDecorator extends BaseDecorator {

  @Override
  public AgentSpan afterStart(final AgentSpan span) {
    assert span != null;
    span.setAttribute(Tags.SPAN_KIND, Tags.SPAN_KIND_SERVER);
    return super.afterStart(span);
  }
}
