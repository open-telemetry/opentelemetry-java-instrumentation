/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.v3_0;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.jms.common.v1_1.DestinationAdapter;
import io.opentelemetry.javaagent.instrumentation.jms.common.v1_1.MessageAdapter;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

public class JakartaMessageAdapter implements MessageAdapter {

  private static final VirtualField<Message, Boolean> RECEIVE_TELEMETRY_RECORDED =
      VirtualField.find(Message.class, Boolean.class);

  public static MessageAdapter create(Message message) {
    return new JakartaMessageAdapter(message);
  }

  private final Message message;

  private JakartaMessageAdapter(Message message) {
    this.message = message;
  }

  @Nullable
  @Override
  public DestinationAdapter getJmsDestination() throws JMSException {
    Destination destination = message.getJMSDestination();
    if (destination == null) {
      return null;
    }
    return JakartaDestinationAdapter.create(destination);
  }

  @Override
  @SuppressWarnings("unchecked") // jms api returns a raw enumeration
  public List<String> getPropertyNames() throws JMSException {
    return Collections.list(message.getPropertyNames());
  }

  @Nullable
  @Override
  public Object getObjectProperty(String key) throws JMSException {
    return message.getObjectProperty(key);
  }

  @Nullable
  @Override
  public String getStringProperty(String key) throws JMSException {
    return message.getStringProperty(key);
  }

  @Override
  public void setStringProperty(String key, String value) throws JMSException {
    message.setStringProperty(key, value);
  }

  @Nullable
  @Override
  public String getJmsCorrelationId() throws JMSException {
    return message.getJMSCorrelationID();
  }

  @Nullable
  @Override
  public String getJmsMessageId() throws JMSException {
    return message.getJMSMessageID();
  }

  @Override
  public boolean wasReceiveTelemetryRecorded() {
    return Boolean.TRUE.equals(RECEIVE_TELEMETRY_RECORDED.get(message));
  }

  @Override
  public void markReceiveTelemetryRecorded() {
    RECEIVE_TELEMETRY_RECORDED.set(message, Boolean.TRUE);
  }
}
