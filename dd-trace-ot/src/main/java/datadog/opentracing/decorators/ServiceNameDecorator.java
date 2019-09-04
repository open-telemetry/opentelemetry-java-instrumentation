package datadog.opentracing.decorators;

import datadog.opentracing.DDSpanContext;
import datadog.trace.api.DDTags;

public class ServiceNameDecorator extends AbstractDecorator {

  private final boolean setTag;

  public ServiceNameDecorator() {
    this(DDTags.SERVICE_NAME, false);
  }

  public ServiceNameDecorator(final String splitByTag, final boolean setTag) {
    super();
    this.setTag = setTag;
    setMatchingTag(splitByTag);
  }

  @Override
  public boolean shouldSetTag(final DDSpanContext context, final String tag, final Object value) {
    context.setServiceName(String.valueOf(value));
    return setTag;
  }
}
