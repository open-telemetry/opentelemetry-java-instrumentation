package datadog.opentracing.decorators;

import datadog.opentracing.DDSpanContext;
import io.opentracing.tag.Tags;

/** Mark all 5xx status codes as an error */
public class Status5XXDecorator extends AbstractDecorator {
  public Status5XXDecorator() {
    super();
    this.setMatchingTag(Tags.HTTP_STATUS.getKey());
  }

  @Override
  public boolean shouldSetTag(final DDSpanContext context, final String tag, final Object value) {
    final int responseCode = Integer.parseInt(value.toString());
    if (500 <= responseCode && responseCode < 600) {
      context.setTag(Tags.ERROR.getKey(), true);
    }
    return true;
  }
}
