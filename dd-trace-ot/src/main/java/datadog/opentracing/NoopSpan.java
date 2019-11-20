package datadog.opentracing;

import io.opentracing.SpanContext;
import java.util.Collections;
import java.util.Map;

public class NoopSpan implements Span {
  public static final NoopSpan INSTANCE = new NoopSpan();

  @Override
  public SpanContext context() {
    return NoopSpanContext.INSTANCE;
  }

  @Override
  public void finish() {}

  @Override
  public void finish(final long finishMicros) {}

  @Override
  public NoopSpan setTag(final String key, final String value) {
    return this;
  }

  @Override
  public NoopSpan setTag(final String key, final boolean value) {
    return this;
  }

  @Override
  public NoopSpan setTag(final String key, final Number value) {
    return this;
  }

  @Override
  public NoopSpan log(final Map<String, ?> fields) {
    return this;
  }

  @Override
  public NoopSpan log(final long timestampMicroseconds, final Map<String, ?> fields) {
    return this;
  }

  @Override
  public NoopSpan log(final String event) {
    return this;
  }

  @Override
  public NoopSpan log(final long timestampMicroseconds, final String event) {
    return this;
  }

  @Override
  public NoopSpan setOperationName(final String operationName) {
    return this;
  }

  static final class NoopSpanContext implements SpanContext {
    static final NoopSpanContext INSTANCE = new NoopSpanContext();

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
}
