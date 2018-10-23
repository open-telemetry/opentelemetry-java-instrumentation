package datadog.opentracing.propagation;

import io.opentracing.SpanContext;
import java.util.Collections;
import java.util.Map;

/**
 * When calling extract, we allow for grabbing other configured headers as tags. Those tags are
 * returned here even if the rest of the request would have returned null.
 */
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
