package datadog.trace.instrumentation.kafka_streams;

import datadog.trace.bootstrap.instrumentation.api.AgentPropagation;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

public class TextMapExtractAdapter implements AgentPropagation.Getter<Headers> {

  public static final TextMapExtractAdapter GETTER = new TextMapExtractAdapter();

  @Override
  public Iterable<String> keys(final Headers headers) {
    final List<String> keys = new ArrayList<>();
    for (final Header header : headers) {
      keys.add(header.key());
    }
    return keys;
  }

  @Override
  public String get(final Headers headers, final String key) {
    final Header header = headers.lastHeader(key);
    if (header == null) {
      return null;
    }
    return new String(header.value(), StandardCharsets.UTF_8);
  }
}
