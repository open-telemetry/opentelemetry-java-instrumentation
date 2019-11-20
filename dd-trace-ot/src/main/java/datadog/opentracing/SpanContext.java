package datadog.opentracing;

// temporary replacement for io.opentracing.SpanContext
public interface SpanContext {

  String toTraceId();

  String toSpanId();
}
