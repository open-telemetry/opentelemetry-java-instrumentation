package io.opentelemetry.auto.instrumentation.rabbitmq.amqp;

import io.opentelemetry.context.propagation.HttpTextFormat;
import java.util.Map;

public class TextMapInjectAdapter implements HttpTextFormat.Setter<Map<String, Object>> {

  public static final TextMapInjectAdapter SETTER = new TextMapInjectAdapter();

  @Override
  public void put(final Map<String, Object> carrier, final String key, final String value) {
    carrier.put(key, value);
  }
}
