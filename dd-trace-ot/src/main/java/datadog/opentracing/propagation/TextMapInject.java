package datadog.opentracing.propagation;

// temporary replacement for io.opentracing.propagation.TextMapInject
public interface TextMapInject {
  void put(String key, String value);
}
