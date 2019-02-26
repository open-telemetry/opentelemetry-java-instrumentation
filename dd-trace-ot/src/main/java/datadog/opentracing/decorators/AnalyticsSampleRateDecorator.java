package datadog.opentracing.decorators;

import datadog.opentracing.DDSpanContext;
import datadog.trace.api.DDTags;

public class AnalyticsSampleRateDecorator extends AbstractDecorator {
  public AnalyticsSampleRateDecorator() {
    super();
    setMatchingTag(DDTags.ANALYTICS_SAMPLE_RATE);
  }

  @Override
  public boolean shouldSetTag(final DDSpanContext context, final String tag, final Object value) {
    if (value instanceof Number) {
      context.setMetric(DDTags.ANALYTICS_SAMPLE_RATE, (Number) value);
    }
    return false;
  }
}
