package datadog.trace.common.processor.rule;

import datadog.opentracing.DDSpan;
import datadog.trace.api.DDTags;
import datadog.trace.common.processor.TraceProcessor;
import java.util.Collection;
import java.util.Map;

/** Converts analytics sample rate tag to metric */
public class AnalyticsSampleRateRule implements TraceProcessor.Rule {
  @Override
  public String[] aliases() {
    return new String[] {"AnalyticsSampleRateDecorator"};
  }

  @Override
  public void processSpan(
      final DDSpan span, final Map<String, Object> tags, final Collection<DDSpan> trace) {
    if (tags.containsKey(DDTags.ANALYTICS_SAMPLE_RATE)) {
      final Object value = tags.get(DDTags.ANALYTICS_SAMPLE_RATE);
      if (value instanceof Number) {
        span.context().setMetric(DDTags.ANALYTICS_SAMPLE_RATE, (Number) value);
      } else if (value instanceof String) {
        try {
          span.context()
              .setMetric(DDTags.ANALYTICS_SAMPLE_RATE, Double.parseDouble((String) value));
        } catch (final NumberFormatException ex) {
          // ignore
        }
      }
      span.setTag(DDTags.ANALYTICS_SAMPLE_RATE, (String) null); // Remove the tag
    }
  }
}
