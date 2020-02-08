package io.opentelemetry.auto.instrumentation.jms;

import io.opentelemetry.context.propagation.HttpTextFormat;
import javax.jms.JMSException;
import javax.jms.Message;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MessageExtractAdapter implements HttpTextFormat.Getter<Message> {

  public static final MessageExtractAdapter GETTER = new MessageExtractAdapter();

  @Override
  public String get(final Message carrier, final String key) {
    final String propName = key.replace("-", MessageInjectAdapter.DASH);
    final Object value;
    try {
      value = carrier.getObjectProperty(propName);
    } catch (final JMSException e) {
      throw new RuntimeException(e);
    }
    if (value instanceof String) {
      return (String) value;
    } else {
      return null;
    }
  }
}
