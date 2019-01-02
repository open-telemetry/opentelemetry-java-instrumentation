package datadog.opentracing.decorators;

import datadog.opentracing.DDSpanContext;
import datadog.trace.api.DDTags;

public class EventSampleRateDecorator extends AbstractDecorator {
  public EventSampleRateDecorator() {
    super();
    setMatchingTag(DDTags.EVENT_SAMPLE_RATE);
  }

  @Override
  public boolean shouldSetTag(final DDSpanContext context, final String tag, final Object value) {
    if (value instanceof Number) {
      context.setMetric(DDTags.EVENT_SAMPLE_RATE, (Number) value);
    }
    return false;
  }
}
