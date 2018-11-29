package datadog.trace.instrumentation.rabbitmq.amqp;

import io.opentracing.propagation.TextMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

// TextMap works with <String,String>, but the type we're given is <String,Object>
public class TextMapExtractAdapter implements TextMap {

  private final Map<String, String> map = new HashMap<>();

  public TextMapExtractAdapter(final Map<String, Object> headers) {
    for (final Map.Entry<String, Object> entry : headers.entrySet()) {
      if (entry != null && entry.getValue() != null) {
        map.put(entry.getKey(), entry.getValue().toString());
      }
    }
  }

  @Override
  public Iterator<Map.Entry<String, String>> iterator() {
    return map.entrySet().iterator();
  }

  @Override
  public void put(final String key, final String value) {
    throw new UnsupportedOperationException("Use inject adapter instead");
  }
}
