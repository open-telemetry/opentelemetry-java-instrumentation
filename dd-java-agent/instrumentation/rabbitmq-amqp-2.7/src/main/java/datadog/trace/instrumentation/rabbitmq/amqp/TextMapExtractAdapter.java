package datadog.trace.instrumentation.rabbitmq.amqp;

import datadog.trace.instrumentation.api.AgentPropagation;
import java.util.Map;

public class TextMapExtractAdapter implements AgentPropagation.Getter<Map<String, Object>> {

  public static final TextMapExtractAdapter GETTER = new TextMapExtractAdapter();

  @Override
  public Iterable<String> keys(final Map<String, Object> carrier) {
    return carrier.keySet();
  }

  @Override
  public String get(final Map<String, Object> carrier, final String key) {
    final Object obj = carrier.get(key);
    return obj == null ? null : obj.toString();
  }
}
