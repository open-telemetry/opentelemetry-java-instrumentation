package datadog.trace.instrumentation.kafka_clients;

import datadog.trace.instrumentation.api.AgentPropagation;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.producer.ProducerRecord;

public class TextMapInjectAdapter implements AgentPropagation.Setter<ProducerRecord> {

  public static final TextMapInjectAdapter SETTER = new TextMapInjectAdapter();

  @Override
  public void set(final ProducerRecord carrier, final String key, final String value) {
    carrier.headers().remove(key).add(key, value.getBytes(StandardCharsets.UTF_8));
  }
}
