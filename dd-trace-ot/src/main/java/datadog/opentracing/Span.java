package datadog.opentracing;

// temporary replacement for io.opentracing.Span
// this is currently needed as superclass for DDSpan and NoopSpan
public interface Span {

  SpanContext context();

  Span setTag(String key, String value);

  Span setTag(String key, boolean value);

  Span setTag(String key, Number value);

  Span setOperationName(String operationName);

  void finish();
}
