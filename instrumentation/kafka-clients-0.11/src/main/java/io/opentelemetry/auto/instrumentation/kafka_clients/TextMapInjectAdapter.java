package io.opentelemetry.auto.instrumentation.kafka_clients;

import io.opentelemetry.context.propagation.HttpTextFormat;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.common.header.Headers;

public class TextMapInjectAdapter implements HttpTextFormat.Setter<Headers> {

  public static final TextMapInjectAdapter SETTER = new TextMapInjectAdapter();

  @Override
  public void put(final Headers headers, final String key, final String value) {
    headers.remove(key).add(key, value.getBytes(StandardCharsets.UTF_8));
  }
}
