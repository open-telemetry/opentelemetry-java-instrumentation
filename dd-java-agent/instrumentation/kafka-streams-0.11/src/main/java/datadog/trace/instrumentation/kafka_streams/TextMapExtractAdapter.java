package datadog.trace.instrumentation.kafka_streams;

import io.opentracing.propagation.TextMap;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

public class TextMapExtractAdapter implements TextMap {

  private final Map<String, String> map = new HashMap<>();

  public TextMapExtractAdapter(final Headers headers) {
    for (final Header header : headers) {
      map.put(header.key(), new String(header.value(), StandardCharsets.UTF_8));
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
