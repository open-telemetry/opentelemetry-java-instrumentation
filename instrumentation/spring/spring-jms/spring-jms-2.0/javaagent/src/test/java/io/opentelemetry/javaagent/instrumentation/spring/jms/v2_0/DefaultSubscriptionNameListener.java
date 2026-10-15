/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.jms.v2_0;

import javax.jms.Message;
import javax.jms.Session;
import org.springframework.jms.listener.SessionAwareMessageListener;

// spring derives the default subscription name from the listener class name, so this listener has
// to be a named class instead of a lambda
class DefaultSubscriptionNameListener implements SessionAwareMessageListener<Message> {

  @Override
  public void onMessage(Message message, Session session) {}
}
