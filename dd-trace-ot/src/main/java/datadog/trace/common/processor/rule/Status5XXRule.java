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
      final DDSpan span, final Map<String, String> meta, final Collection<DDSpan> trace) {
    if (!span.context().getErrorFlag() && meta.containsKey(Tags.HTTP_STATUS.getKey())) {
      final int responseCode = Integer.parseInt(meta.get(Tags.HTTP_STATUS.getKey()));
      span.setError(500 <= responseCode && responseCode < 600);
    }
  }
}
