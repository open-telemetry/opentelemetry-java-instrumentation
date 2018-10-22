package datadog.opentracing.propagation;

import io.opentracing.SpanContext;
import java.util.Collections;
import java.util.Map;

public class TagContext implements SpanContext {
  private final Map<String, String> tags;

  public TagContext(final Map<String, String> tags) {
    this.tags = tags;
  }

  public Map<String, String> getTags() {
    return tags;
  }

  @Override
  public Iterable<Map.Entry<String, String>> baggageItems() {
    return Collections.emptyList();
  }
}
