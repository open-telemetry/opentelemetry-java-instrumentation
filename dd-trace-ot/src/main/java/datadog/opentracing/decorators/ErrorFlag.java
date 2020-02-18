package datadog.opentracing.decorators;

import datadog.opentracing.DDSpanContext;
import io.opentracing.tag.Tags;

public class ErrorFlag extends AbstractDecorator {
  public ErrorFlag() {
    super();
    setMatchingTag(Tags.ERROR.getKey());
  }

  @Override
  public boolean shouldSetTag(final DDSpanContext context, final String tag, final Object value) {
    // Assign resource name
    try {
      context.setErrorFlag(Boolean.parseBoolean(String.valueOf(value)));
    } catch (final Throwable t) {
      // DO NOTHING
    }
    return false;
  }
}
