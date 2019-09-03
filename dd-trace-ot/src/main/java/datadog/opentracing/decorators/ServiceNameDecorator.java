package datadog.opentracing.decorators;

import datadog.opentracing.DDSpanContext;
import datadog.trace.api.DDTags;

public class ServiceNameDecorator extends AbstractDecorator {

  public ServiceNameDecorator() {
    super();
    setMatchingTag(DDTags.SERVICE_NAME);
  }

  public ServiceNameDecorator(final String splitByTag) {
    super();
    setMatchingTag(splitByTag);
  }

  @Override
  public boolean shouldSetTag(final DDSpanContext context, final String tag, final Object value) {
    context.setServiceName(String.valueOf(value));
    return false;
  }
}
