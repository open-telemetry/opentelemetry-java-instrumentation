package datadog.opentracing.decorators;

import datadog.opentracing.DDSpanContext;
import datadog.trace.api.sampling.ForcedTracing;
import datadog.trace.api.sampling.PrioritySampling;

/**
 * Tag decorator to replace tag 'manual.keep: true' with the appropriate priority sampling value.
 */
public class ForceManualKeepDecorator extends AbstractDecorator {

  public ForceManualKeepDecorator() {
    super();
    setMatchingTag(ForcedTracing.manual_KEEP);
  }

  @Override
  public boolean shouldSetTag(final DDSpanContext context, final String tag, final Object value) {
    if (value instanceof Boolean && (boolean)value) {
      context.setSamplingPriority(PrioritySampling.USER_KEEP);
    }
    return false;
  }
}
