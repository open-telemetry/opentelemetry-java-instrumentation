package datadog.trace.instrumentation.jms;

import datadog.trace.instrumentation.api.AgentPropagation;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import javax.jms.JMSException;
import javax.jms.Message;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MessagePropertyTextMap
    implements AgentPropagation.Getter<Message>, AgentPropagation.Setter<Message> {

  public static final MessagePropertyTextMap GETTER = new MessagePropertyTextMap();
  public static final MessagePropertyTextMap SETTER = new MessagePropertyTextMap();

  static final String DASH = "__dash__";

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
            keys.add(key.replace(DASH, "-"));
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
    final String propName = key.replace("-", DASH);
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

  @Override
  public void set(final Message carrier, final String key, final String value) {
    final String propName = key.replace("-", DASH);
    try {
      carrier.setStringProperty(propName, value);
    } catch (final JMSException e) {
      if (log.isDebugEnabled()) {
        log.debug("Failure setting jms property: " + propName, e);
      }
    }
  }
}
