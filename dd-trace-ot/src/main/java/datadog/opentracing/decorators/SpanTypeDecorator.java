package datadog.opentracing.decorators;

import datadog.opentracing.DDSpanContext;
import datadog.trace.api.DDTags;

public class SpanTypeDecorator extends AbstractDecorator {

  public SpanTypeDecorator() {
    super();
    this.setMatchingTag(DDTags.SPAN_TYPE);
  }

  @Override
  public boolean shouldSetTag(final DDSpanContext context, final String tag, final Object value) {
    context.setSpanType(String.valueOf(value));
    // TODO: Do we really want a span type tag since it already exists on the span?
    return false;
  }
}
