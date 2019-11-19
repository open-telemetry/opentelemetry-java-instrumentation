package datadog.opentracing.propagation;

import io.opentracing.SpanContext;
import java.util.Collections;
import java.util.Map;

public class TagContext implements SpanContext {
  private final String origin;

  public TagContext(final String origin) {
    this.origin = origin;
  }

  public String getOrigin() {
    return origin;
  }

  @Override
  public String toTraceId() {
    return "";
  }

  @Override
  public String toSpanId() {
    return "";
  }

  @Override
  public Iterable<Map.Entry<String, String>> baggageItems() {
    return Collections.emptyList();
  }
}
