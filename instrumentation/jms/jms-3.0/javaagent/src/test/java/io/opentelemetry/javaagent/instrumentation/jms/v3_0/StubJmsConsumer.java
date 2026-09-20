/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.v3_0;

import jakarta.jms.JMSConsumer;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;

/**
 * A {@link JMSConsumer} that never delegates to a {@code MessageConsumer}. The receive methods
 * return the given message (or {@code null}), so a span can only come from the simplified-API
 * instrumentation.
 */
class StubJmsConsumer implements JMSConsumer {

  private final Message message;

  StubJmsConsumer(Message message) {
    this.message = message;
  }

  @Override
  public String getMessageSelector() {
    throw new UnsupportedOperationException();
  }

  @Override
  public MessageListener getMessageListener() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setMessageListener(MessageListener listener) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Message receive() {
    return message;
  }

  @Override
  public Message receive(long timeout) {
    return message;
  }

  @Override
  public Message receiveNoWait() {
    return message;
  }

  @Override
  public void close() {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> T receiveBody(Class<T> type) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> T receiveBody(Class<T> type, long timeout) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> T receiveBodyNoWait(Class<T> type) {
    throw new UnsupportedOperationException();
  }
}
