package io.opentelemetry.auto.instrumentation.kafka_clients;

import io.opentelemetry.auto.instrumentation.api.AgentPropagation;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.common.header.Headers;

public class TextMapInjectAdapter implements AgentPropagation.Setter<Headers> {

  public static final TextMapInjectAdapter SETTER = new TextMapInjectAdapter();

  @Override
  public void set(final Headers headers, final String key, final String value) {
    headers.remove(key).add(key, value.getBytes(StandardCharsets.UTF_8));
  }
}
