package datadog.trace.common.processor.rule;

import datadog.opentracing.DDSpan;
import datadog.trace.common.processor.TraceProcessor;
import io.opentracing.tag.Tags;
import java.util.Collection;
import java.util.Map;

/** Converts error tag to field */
public class ErrorRule implements TraceProcessor.Rule {
  @Override
  public String[] aliases() {
    return new String[] {"ErrorFlag"};
  }

  @Override
  public void processSpan(
      final DDSpan span, final Map<String, Object> tags, final Collection<DDSpan> trace) {
    final Object value = tags.get(Tags.ERROR.getKey());
    if (value instanceof Boolean) {
      span.setError((Boolean) value);
    } else if (value != null) {
      span.setError(Boolean.parseBoolean(value.toString()));
    }

    if (tags.containsKey(Tags.ERROR.getKey())) {
      span.setTag(Tags.ERROR, null); // Remove the tag
    }
  }
}
