package datadog.trace.instrumentation.kafka_clients;

import io.opentracing.propagation.TextMap;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import org.apache.kafka.common.header.Headers;

public class TextMapInjectAdapter implements TextMap {

  private final Headers headers;

  public TextMapInjectAdapter(final Headers headers) {
    this.headers = headers;
  }

  @Override
  public Iterator<Map.Entry<String, String>> iterator() {
    throw new UnsupportedOperationException("Use extract adapter instead");
  }

  @Override
  public void put(final String key, final String value) {
    headers.remove(key).add(key, value.getBytes(StandardCharsets.UTF_8));
  }
}
