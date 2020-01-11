package datadog.trace.agent.decorator;

import datadog.trace.api.MoreTags;
import datadog.trace.instrumentation.api.AgentSpan;
import datadog.trace.instrumentation.api.Tags;

public abstract class ClientDecorator extends BaseDecorator {

  protected abstract String service();

  protected String spanKind() {
    return Tags.SPAN_KIND_CLIENT;
  }

  @Override
  public AgentSpan afterStart(final AgentSpan span) {
    assert span != null;
    if (service() != null) {
      span.setTag(MoreTags.SERVICE_NAME, service());
    }
    span.setTag(Tags.SPAN_KIND, spanKind());
    return super.afterStart(span);
  }
}
