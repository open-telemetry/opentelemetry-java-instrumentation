/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.v1_1;

import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType.RECEIVE;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignal.CONSUMED_MESSAGES;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignal.SPAN;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignals;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.messaging.MessagingTelemetryCarrier;
import io.opentelemetry.javaagent.instrumentation.jms.common.v1_1.DestinationAdapter;
import io.opentelemetry.javaagent.instrumentation.jms.common.v1_1.MessageAdapter;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;

public class JavaxMessageAdapter implements MessageAdapter {

  private static final MessagingTelemetryCarrier<Message> messageTelemetry =
      MessagingTelemetryCarrier.create(
          VirtualField.find(Message.class, MessagingTelemetrySignals.class));

  public static MessageAdapter create(Message message) {
    return new JavaxMessageAdapter(message);
  }

  private final Message message;

  private JavaxMessageAdapter(Message message) {
    this.message = message;
  }

  @Nullable
  @Override
  public DestinationAdapter getJmsDestination() throws JMSException {
    Destination destination = message.getJMSDestination();
    if (destination == null) {
      return null;
    }
    return JavaxDestinationAdapter.create(destination);
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
  public boolean wereConsumedMessagesRecorded() {
    return messageTelemetry.contains(message, RECEIVE, CONSUMED_MESSAGES);
  }

  @Override
  public void markReceiveSpanRecorded() {
    messageTelemetry.add(message, RECEIVE, SPAN);
  }

  @Override
  public void markConsumedMessagesRecorded() {
    messageTelemetry.add(message, RECEIVE, CONSUMED_MESSAGES);
  }
}
