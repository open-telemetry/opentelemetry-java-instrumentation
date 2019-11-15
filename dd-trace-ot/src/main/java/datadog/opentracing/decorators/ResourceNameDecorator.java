package datadog.opentracing.decorators;

import datadog.opentracing.DDSpanContext;
import datadog.trace.api.DDTags;

public class ResourceNameDecorator extends AbstractDecorator {

  public ResourceNameDecorator() {
    super();
    this.setMatchingTag(DDTags.RESOURCE_NAME);
  }

  @Override
  public boolean shouldSetTag(final DDSpanContext context, final String tag, final Object value) {
    context.setResourceName(String.valueOf(value));
    return false;
  }
}
