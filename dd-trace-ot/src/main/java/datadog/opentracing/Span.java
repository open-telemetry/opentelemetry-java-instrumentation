package datadog.opentracing;

import java.util.Map;

// temporary replacement for io.opentracing.Span
// this is currently needed as superclass for DDSpan and NoopSpan
public interface Span {

  SpanContext context();

  Span setTag(String key, String value);

  Span setTag(String key, boolean value);

  Span setTag(String key, Number value);

  Span log(Map<String, ?> fields);

  Span setOperationName(String operationName);

  void finish();
}
