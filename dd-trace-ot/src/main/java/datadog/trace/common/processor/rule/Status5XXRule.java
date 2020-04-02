package datadog.trace.common.processor.rule;

import datadog.opentracing.DDSpan;
import datadog.trace.common.processor.TraceProcessor;
import io.opentracing.tag.Tags;
import java.util.Collection;
import java.util.Map;

/** Mark all 5xx status codes as an error */
public class Status5XXRule implements TraceProcessor.Rule {
  @Override
  public String[] aliases() {
    return new String[] {"Status5XXDecorator"};
  }

  @Override
  public void processSpan(
      final DDSpan span, final Map<String, Object> tags, final Collection<DDSpan> trace) {
    if (!span.context().getErrorFlag() && tags.containsKey(Tags.HTTP_STATUS.getKey())) {
      final Object value = tags.get(Tags.HTTP_STATUS.getKey());
      try {
        final int responseCode =
            value instanceof Integer ? (int) value : Integer.parseInt(value.toString());
        span.setError(500 <= responseCode && responseCode < 600);
      } catch (final NumberFormatException ex) {
        // If using Tags.HTTP_STATUS, value should always be an Integer,
        // but lets catch NumberFormatException just to be safe.
      }
    }
  }
}
