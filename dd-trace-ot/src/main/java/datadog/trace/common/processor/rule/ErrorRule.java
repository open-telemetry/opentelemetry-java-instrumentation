package datadog.trace.common.processor.rule;

import datadog.opentracing.DDSpan;
import datadog.trace.common.processor.TraceProcessor;
import io.opentracing.tag.Tags;
import java.util.Collection;
import java.util.Map;

/** Converts error tag to error field */
public class ErrorRule implements TraceProcessor.Rule {
  @Override
  public String[] aliases() {
    return new String[] {"ErrorFlag"};
  }

  @Override
  public void processSpan(
      final DDSpan span, final Map<String, String> meta, final Collection<DDSpan> trace) {
    if (meta.containsKey(Tags.ERROR.getKey())) {
      span.setError(Boolean.parseBoolean(meta.get(Tags.ERROR.getKey())));
      span.setTag(Tags.ERROR, null); // Remove the tag
    }
  }
}
