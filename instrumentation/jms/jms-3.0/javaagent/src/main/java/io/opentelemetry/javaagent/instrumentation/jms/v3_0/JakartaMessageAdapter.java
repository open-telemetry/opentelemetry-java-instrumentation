/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.v3_0;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.jms.JmsMessageDeliveryState;
import io.opentelemetry.javaagent.bootstrap.jms.JmsReceiveContext;
import io.opentelemetry.javaagent.instrumentation.jms.common.v1_1.DestinationAdapter;
import io.opentelemetry.javaagent.instrumentation.jms.common.v1_1.MessageAdapter;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

public class JakartaMessageAdapter implements MessageAdapter {

  private static final VirtualField<Message, JmsReceiveContext> RECEIVE_CONTEXT =
      VirtualField.find(Message.class, JmsReceiveContext.class);
  private static final VirtualField<Message, JmsMessageDeliveryState> DELIVERY_STATE =
      VirtualField.find(Message.class, JmsMessageDeliveryState.class);

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
  public void prepareForReceive() {
    RECEIVE_CONTEXT.set(message, null);
    JmsMessageDeliveryState state = new JmsMessageDeliveryState();
    state.prepareForReceive();
    DELIVERY_STATE.set(message, state);
  }

  @Override
  public void setReceiveContext(JmsReceiveContext context) {
    RECEIVE_CONTEXT.set(message, context);
  }

  @Nullable
  @Override
  public JmsReceiveContext getReceiveContext() {
    return RECEIVE_CONTEXT.get(message);
  }

  @Override
  public void beginProcessing() {
    if (!getOrCreateDeliveryState().beginProcessing()) {
      RECEIVE_CONTEXT.set(message, null);
    }
  }

  @Override
  public void endProcessing() {
    JmsMessageDeliveryState state = DELIVERY_STATE.get(message);
    if (state != null && state.endProcessing()) {
      RECEIVE_CONTEXT.set(message, null);
    }
  }

  @Override
  public boolean claimConsumedMessages() {
    return getOrCreateDeliveryState().claimConsumedMessages();
  }

  private JmsMessageDeliveryState getOrCreateDeliveryState() {
    JmsMessageDeliveryState state = DELIVERY_STATE.get(message);
    if (state != null) {
      return state;
    }
    synchronized (message) {
      state = DELIVERY_STATE.get(message);
      if (state == null) {
        state = new JmsMessageDeliveryState();
        DELIVERY_STATE.set(message, state);
      }
      return state;
    }
  }
}
