package datadog.trace.instrumentation.jms;

import datadog.trace.bootstrap.instrumentation.api.AgentPropagation;
import javax.jms.JMSException;
import javax.jms.Message;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MessageInjectAdapter implements AgentPropagation.Setter<Message> {

  public static final MessageInjectAdapter SETTER = new MessageInjectAdapter();

  static final String DASH = "__dash__";

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
