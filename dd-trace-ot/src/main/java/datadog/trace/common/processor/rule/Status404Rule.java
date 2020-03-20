package datadog.trace.common.processor.rule;

import datadog.opentracing.DDSpan;
import datadog.trace.common.processor.TraceProcessor;
import io.opentracing.tag.Tags;
import java.util.Collection;
import java.util.Map;

/** This span decorator protect against spam on the resource name */
public class Status404Rule implements TraceProcessor.Rule {
  @Override
  public String[] aliases() {
    return new String[] {"Status404Decorator"};
  }

  @Override
  public void processSpan(
      final DDSpan span, final Map<String, Object> tags, final Collection<DDSpan> trace) {
    if (!span.context().isResourceNameSet() && "404".equals(tags.get(Tags.HTTP_STATUS.getKey()))) {
      span.setResourceName("404");
    }
  }
}
