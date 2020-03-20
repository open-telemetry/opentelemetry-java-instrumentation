package datadog.trace.common.processor.rule;

import datadog.opentracing.DDSpan;
import datadog.trace.api.DDTags;
import datadog.trace.common.processor.TraceProcessor;
import java.util.Collection;
import java.util.Map;

/** Converts resource name tag to field */
public class ResourceNameRule implements TraceProcessor.Rule {
  @Override
  public String[] aliases() {
    return new String[] {"ResourceNameDecorator"};
  }

  @Override
  public void processSpan(
      final DDSpan span, final Map<String, Object> tags, final Collection<DDSpan> trace) {
    if (tags.containsKey(DDTags.RESOURCE_NAME)) {
      span.setResourceName(tags.get(DDTags.RESOURCE_NAME).toString());
      span.setTag(DDTags.RESOURCE_NAME, (String) null); // Remove the tag
    }
  }
}
