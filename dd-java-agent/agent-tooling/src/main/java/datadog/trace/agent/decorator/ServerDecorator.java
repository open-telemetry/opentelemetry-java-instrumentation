package datadog.trace.agent.decorator;

import datadog.trace.instrumentation.api.AgentSpan;
import io.opentracing.tag.Tags;

public abstract class ServerDecorator extends BaseDecorator {

  @Override
  public AgentSpan afterStart(final AgentSpan span) {
    assert span != null;
    span.setTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER);
    return super.afterStart(span);
  }
}
