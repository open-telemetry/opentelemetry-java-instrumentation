/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.jms.v6_0;

import static io.opentelemetry.instrumentation.testing.GlobalTraceUtil.runWithSpan;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.TextMessage;
import java.util.concurrent.CompletableFuture;
import org.springframework.context.annotation.Bean;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.annotation.JmsListenerConfigurer;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.config.JmsListenerEndpoint;
import org.springframework.jms.listener.AbstractMessageListenerContainer;
import org.springframework.jms.listener.MessageListenerContainer;
import org.springframework.jms.listener.SessionAwareMessageListener;

@EnableJms
class SharedSubscriptionConfig extends AbstractConfig {

  // the broker is shared by all tests in the class, so this listener uses a topic of its own
  static final String DESTINATION_NAME = "spring-jms-shared-subscription";
  static final String SUBSCRIPTION_NAME = "shared-subscription";

  @Bean
  JmsListenerConfigurer sharedSubscriptionConfigurer(
      ConnectionFactory connectionFactory, CompletableFuture<String> receivedMessage) {
    // a shared, non durable subscription, registered with a container factory of its own so that it
    // doesn't inherit the durable subscription settings that the other tests use
    DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
    factory.setConnectionFactory(connectionFactory);
    factory.setPubSubDomain(true);
    factory.setSubscriptionShared(true);

    return registrar ->
        registrar.registerEndpoint(
            new JmsListenerEndpoint() {
              @Override
              public String getId() {
                return "shared-subscription-name";
              }

              @Override
              public void setupListenerContainer(MessageListenerContainer listenerContainer) {
                AbstractMessageListenerContainer container =
                    (AbstractMessageListenerContainer) listenerContainer;
                container.setDestinationName(DESTINATION_NAME);
                container.setSubscriptionName(SUBSCRIPTION_NAME);
                container.setupMessageListener(
                    (SessionAwareMessageListener<TextMessage>)
                        (message, session) ->
                            runWithSpan(
                                "consumer", () -> receivedMessage.complete(message.getText())));
              }
            },
            factory);
  }
}
