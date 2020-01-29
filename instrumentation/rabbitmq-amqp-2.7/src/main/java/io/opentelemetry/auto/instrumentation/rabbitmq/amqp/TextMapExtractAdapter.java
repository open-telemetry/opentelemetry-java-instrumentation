package io.opentelemetry.auto.instrumentation.rabbitmq.amqp;

import io.opentelemetry.context.propagation.HttpTextFormat;
import java.util.Map;

public class TextMapExtractAdapter implements HttpTextFormat.Getter<Map<String, Object>> {

  public static final TextMapExtractAdapter GETTER = new TextMapExtractAdapter();

  @Override
  public String get(final Map<String, Object> carrier, final String key) {
    final Object obj = carrier.get(key);
    return obj == null ? null : obj.toString();
  }
}
