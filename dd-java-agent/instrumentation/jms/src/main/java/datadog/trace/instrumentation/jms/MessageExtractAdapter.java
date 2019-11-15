package datadog.trace.instrumentation.jms;

import datadog.trace.instrumentation.api.AgentPropagation;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import javax.jms.JMSException;
import javax.jms.Message;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MessageExtractAdapter implements AgentPropagation.Getter<Message> {

  public static final MessageExtractAdapter GETTER = new MessageExtractAdapter();

  @Override
  public Iterable<String> keys(final Message carrier) {
    final List<String> keys = new ArrayList<>();
    try {
      final Enumeration<?> enumeration = carrier.getPropertyNames();
      if (enumeration != null) {
        while (enumeration.hasMoreElements()) {
          final String key = (String) enumeration.nextElement();
          final Object value = carrier.getObjectProperty(key);
          if (value instanceof String) {
            keys.add(key.replace(MessageInjectAdapter.DASH, "-"));
          }
        }
      }
    } catch (final JMSException e) {
      throw new RuntimeException(e);
    }
    return keys;
  }

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
