package datadog.opentracing.decorators;

import datadog.opentracing.DDSpanContext;
import datadog.trace.api.DDTags;
import datadog.trace.api.sampling.PrioritySampling;

/**
 * Tag decorator to replace tag 'manual.drop: true' with the appropriate priority sampling value.
 */
public class ForceManualDropDecorator extends AbstractDecorator {

  public ForceManualDropDecorator() {
    super();
    setMatchingTag(DDTags.MANUAL_DROP);
  }

  @Override
  public boolean shouldSetTag(final DDSpanContext context, final String tag, final Object value) {
    if (value instanceof Boolean && (boolean) value) {
      context.setSamplingPriority(PrioritySampling.USER_DROP);
    }
    return false;
  }
}
