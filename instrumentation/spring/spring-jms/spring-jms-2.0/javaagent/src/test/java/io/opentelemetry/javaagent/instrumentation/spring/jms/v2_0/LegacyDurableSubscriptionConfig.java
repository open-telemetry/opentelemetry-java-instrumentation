/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.jms.v2_0;

import javax.jms.ConnectionFactory;
import javax.jms.Message;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.annotation.JmsListenerConfigurer;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.config.JmsListenerContainerFactory;
import org.springframework.jms.config.JmsListenerEndpoint;
import org.springframework.jms.config.JmsListenerEndpointRegistrar;
import org.springframework.jms.listener.AbstractMessageListenerContainer;
import org.springframework.jms.listener.MessageListenerContainer;
import org.springframework.jms.listener.SessionAwareMessageListener;

@EnableJms
class LegacyDurableSubscriptionConfig extends AbstractConfig implements JmsListenerConfigurer {

  @Bean
  @Override
  JmsListenerContainerFactory<?> jmsListenerContainerFactory(ConnectionFactory connectionFactory) {
    DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
    factory.setConnectionFactory(connectionFactory);
    factory.setPubSubDomain(true);
    factory.setClientId("spring-jms-2-legacy-test");
    return factory;
  }

  @Override
  public void configureJmsListeners(JmsListenerEndpointRegistrar registrar) {
    registrar.registerEndpoint(
        new JmsListenerEndpoint() {
          @Override
          public @NotNull String getId() {
            return "legacy-durable-subscription";
          }

          @SuppressWarnings("deprecation") // testing the legacy Spring 2.x setter
          @Override
          public void setupListenerContainer(@NotNull MessageListenerContainer listenerContainer) {
            AbstractMessageListenerContainer container =
                (AbstractMessageListenerContainer) listenerContainer;
            container.setDestinationName("SpringListenerJms2");
            container.setDurableSubscriptionName("legacy-durable-subscription");
            container.setupMessageListener(
                (SessionAwareMessageListener<Message>) (message, session) -> {});
          }
        });
  }
}
