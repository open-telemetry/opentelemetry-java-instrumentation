package datadog.trace.instrumentation.kafka_clients;

import datadog.trace.instrumentation.api.AgentPropagation;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;

public class TextMapExtractAdapter implements AgentPropagation.Getter<ConsumerRecord> {

  public static final TextMapExtractAdapter GETTER = new TextMapExtractAdapter();

  @Override
  public Iterable<String> keys(final ConsumerRecord carrier) {
    final List<String> keys = new ArrayList<>();
    for (final Header header : carrier.headers()) {
      keys.add(header.key());
    }
    return keys;
  }

  @Override
  public String get(final ConsumerRecord carrier, final String key) {
    final Header header = carrier.headers().lastHeader(key);
    if (header == null) {
      return null;
    }
    return new String(header.value(), StandardCharsets.UTF_8);
  }
}
