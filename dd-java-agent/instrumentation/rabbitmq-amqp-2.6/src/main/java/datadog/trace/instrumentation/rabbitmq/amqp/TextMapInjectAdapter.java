package datadog.trace.instrumentation.rabbitmq.amqp;

import io.opentracing.propagation.TextMap;
import java.util.Iterator;
import java.util.Map;

// TextMap works with <String,String>, but the type we're given is <String,Object>
public class TextMapInjectAdapter implements TextMap {
  private final Map<String, ? super String> map;

  public TextMapInjectAdapter(final Map<String, ? super String> map) {
    this.map = map;
  }

  @Override
  public Iterator<Map.Entry<String, String>> iterator() {
    throw new UnsupportedOperationException(
        "TextMapInjectAdapter should only be used with Tracer.inject()");
  }

  @Override
  public void put(final String key, final String value) {
    map.put(key, value);
  }
}
