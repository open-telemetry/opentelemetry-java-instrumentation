package datadog.opentracing.propagation;

import java.util.Map;

// temporary replacement for io.opentracing.propagation.TextMapInjectAdapter
public class TextMapInjectAdapter implements TextMapInject {
  protected final Map<String, ? super String> map;

  public TextMapInjectAdapter(final Map<String, ? super String> map) {
    this.map = map;
  }

  @Override
  public void put(final String key, final String value) {
    map.put(key, value);
  }
}
