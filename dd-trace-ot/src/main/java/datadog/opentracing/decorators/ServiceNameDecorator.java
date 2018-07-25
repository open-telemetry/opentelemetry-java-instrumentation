package datadog.opentracing.decorators;

import datadog.opentracing.DDSpanContext;
import datadog.trace.api.DDTags;

public class ServiceNameDecorator extends AbstractDecorator {

  public ServiceNameDecorator() {
    super();
    this.setMatchingTag(DDTags.SERVICE_NAME);
  }

  @Override
  public boolean shouldSetTag(final DDSpanContext context, final String tag, final Object value) {
    context.setServiceName(String.valueOf(value));
    return false;
  }
}
