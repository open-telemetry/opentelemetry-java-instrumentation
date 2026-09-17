/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.jms.v6_0;

import java.util.concurrent.CompletableFuture;
import org.springframework.context.annotation.Bean;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.annotation.JmsListenerConfigurer;
import org.springframework.jms.config.JmsListenerEndpoint;
import org.springframework.jms.listener.AbstractMessageListenerContainer;
import org.springframework.jms.listener.MessageListenerContainer;

@EnableJms
class DefaultSubscriptionNameConfig extends AbstractConfig {

  // the broker is shared by all tests in the class, and durable subscriptions outlive the
  // application context that created them, so this listener uses a topic of its own to avoid
  // leaving messages behind in the durable subscriptions used by the other tests
  static final String DESTINATION_NAME = "spring-jms-default-subscription";

  @Bean
  JmsListenerConfigurer jmsListenerConfigurer(CompletableFuture<String> receivedMessage) {
    return registrar ->
        registrar.registerEndpoint(
            new JmsListenerEndpoint() {
              @Override
              public String getId() {
                return "default-subscription-name";
              }

              @Override
              public void setupListenerContainer(MessageListenerContainer listenerContainer) {
                AbstractMessageListenerContainer container =
                    (AbstractMessageListenerContainer) listenerContainer;
                container.setDestinationName(DESTINATION_NAME);
                // deliberately not calling setSubscriptionName, so that spring falls back to the
                // default subscription name derived from the listener class
                container.setupMessageListener(
                    new DefaultSubscriptionNameListener(receivedMessage));
              }
            });
  }
}
