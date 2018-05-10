package datadog.opentracing.decorators;

import datadog.opentracing.DDSpanContext;
import datadog.trace.api.DDTags;
import io.opentracing.tag.Tags;

/** This span decorator protect against spam on the resource name */
public class Status404Decorator extends AbstractDecorator {

  public Status404Decorator() {
    super();
    this.setMatchingTag(Tags.HTTP_STATUS.getKey());
    this.setMatchingValue(404);
    this.setReplacementTag(DDTags.RESOURCE_NAME);
    this.setReplacementValue("404");
  }

  @Override
  public boolean shouldSetTag(final DDSpanContext context, final String tag, final Object value) {
    super.shouldSetTag(context, tag, value);
    return true;
  }
}
