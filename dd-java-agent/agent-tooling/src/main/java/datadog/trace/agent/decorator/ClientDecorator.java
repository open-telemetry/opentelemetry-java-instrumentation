package datadog.trace.agent.decorator;

import datadog.trace.api.DDTags;
import io.opentracing.Span;
import io.opentracing.tag.Tags;

public abstract class ClientDecorator extends BaseDecorator {

  protected abstract String service();

  @Override
  public Span afterStart(final Span span) {
    assert span != null;
    if (service() != null) {
      span.setTag(DDTags.SERVICE_NAME, service());
    }
    Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_CLIENT);
    return super.afterStart(span);
  }
}
