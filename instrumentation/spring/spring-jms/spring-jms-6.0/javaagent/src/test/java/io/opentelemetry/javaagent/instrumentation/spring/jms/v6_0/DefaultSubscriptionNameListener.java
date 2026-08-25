/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.jms.v6_0;

import static io.opentelemetry.instrumentation.testing.GlobalTraceUtil.runWithSpan;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import java.util.concurrent.CompletableFuture;
import org.springframework.jms.listener.SessionAwareMessageListener;

// spring derives the default subscription name from the listener class name, so this listener has
// to be a named class instead of a lambda
class DefaultSubscriptionNameListener implements SessionAwareMessageListener<Message> {

  private final CompletableFuture<String> receivedMessage;

  DefaultSubscriptionNameListener(CompletableFuture<String> receivedMessage) {
    this.receivedMessage = receivedMessage;
  }

  @Override
  public void onMessage(Message message, Session session) throws JMSException {
    TextMessage textMessage = (TextMessage) message;
    runWithSpan("consumer", () -> receivedMessage.complete(textMessage.getText()));
  }
}
