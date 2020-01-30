package io.opentelemetry.auto.instrumentation.kafka_clients;

import io.opentelemetry.context.propagation.HttpTextFormat;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

public class TextMapExtractAdapter implements HttpTextFormat.Getter<Headers> {

  public static final TextMapExtractAdapter GETTER = new TextMapExtractAdapter();

  @Override
  public String get(final Headers headers, final String key) {
    final Header header = headers.lastHeader(key);
    if (header == null) {
      return null;
    }
    return new String(header.value(), StandardCharsets.UTF_8);
  }
}
