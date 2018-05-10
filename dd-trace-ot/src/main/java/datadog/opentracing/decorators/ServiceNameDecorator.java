package datadog.opentracing.decorators;

import datadog.opentracing.DDSpanContext;
import datadog.trace.api.DDTags;
import java.util.Map;

public class ServiceNameDecorator extends AbstractDecorator {

  private final Map<String, String> mappings;

  public ServiceNameDecorator(final Map<String, String> mappings) {
    super();
    this.setMatchingTag(DDTags.SERVICE_NAME);
    this.mappings = mappings;
  }

  @Override
  public boolean shouldSetTag(final DDSpanContext context, final String tag, final Object value) {
    if (mappings.containsKey(String.valueOf(value))) {
      context.setServiceName(mappings.get(String.valueOf(value)));
    } else {
      context.setServiceName(String.valueOf(value));
    }
    return false;
  }
}
