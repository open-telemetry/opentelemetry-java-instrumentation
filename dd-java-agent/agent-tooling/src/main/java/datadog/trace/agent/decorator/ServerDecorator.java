package datadog.trace.agent.decorator;

import datadog.trace.api.Config;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import io.opentracing.tag.Tags;

public abstract class ServerDecorator extends BaseDecorator {

  @Override
  public AgentSpan afterStart(final AgentSpan span) {
    assert span != null;
    span.setTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER);
    span.setTag(Config.LANGUAGE_TAG_KEY, Config.LANGUAGE_TAG_VALUE);
    return super.afterStart(span);
  }
}
