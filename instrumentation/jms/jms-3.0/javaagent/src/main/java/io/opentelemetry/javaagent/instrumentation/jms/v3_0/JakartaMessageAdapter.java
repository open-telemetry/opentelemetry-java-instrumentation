/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.v3_0;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.jms.JmsMessageProcessingState;
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
  private static final VirtualField<Message, JmsMessageProcessingState> PROCESSING_STATE =
      VirtualField.find(Message.class, JmsMessageProcessingState.class);

  public static JakartaMessageAdapter create(Message message) {
    return new JakartaMessageAdapter(message);
  }

  private final Message message;
  @Nullable private JmsMessageProcessingState processingState;

  private JakartaMessageAdapter(Message message) {
    this.message = message;
    processingState = PROCESSING_STATE.get(message);
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
  public JmsMessageProcessingState prepareForReceive() {
    RECEIVE_CONTEXT.set(message, null);
    processingState = new JmsMessageProcessingState();
    PROCESSING_STATE.set(message, processingState);
    return processingState;
  }

  @Override
  public void setReceiveContext(JmsReceiveContext context) {
    if (PROCESSING_STATE.get(message) == context.processingState()) {
      RECEIVE_CONTEXT.set(message, context);
    }
  }

  @Nullable
  @Override
  public JmsReceiveContext getReceiveContext() {
    JmsReceiveContext receiveContext = RECEIVE_CONTEXT.get(message);
    return receiveContext != null && receiveContext.processingState() == processingState
        ? receiveContext
        : null;
  }

  @Override
  public boolean beginProcessing() {
    JmsMessageProcessingState state = processingState;
    if (state == null || state.isProcessingCompleted()) {
      state = new JmsMessageProcessingState();
      processingState = state;
      PROCESSING_STATE.set(message, state);
      RECEIVE_CONTEXT.set(message, null);
    }
    return state.beginProcessing();
  }

  @Override
  public void endProcessing() {
    JmsMessageProcessingState state = processingState;
    if (state != null && state.endProcessing() && PROCESSING_STATE.get(message) == state) {
      RECEIVE_CONTEXT.set(message, null);
    }
  }
}
