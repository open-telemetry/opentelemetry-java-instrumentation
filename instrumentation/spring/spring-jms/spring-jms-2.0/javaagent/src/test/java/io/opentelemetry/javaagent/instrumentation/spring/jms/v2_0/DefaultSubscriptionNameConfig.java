/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.jms.v2_0;

import org.jetbrains.annotations.NotNull;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.annotation.JmsListenerConfigurer;
import org.springframework.jms.config.JmsListenerEndpoint;
import org.springframework.jms.config.JmsListenerEndpointRegistrar;
import org.springframework.jms.listener.AbstractMessageListenerContainer;
import org.springframework.jms.listener.MessageListenerContainer;

@EnableJms
class DefaultSubscriptionNameConfig extends AbstractConfig implements JmsListenerConfigurer {

  @Override
  public void configureJmsListeners(JmsListenerEndpointRegistrar registrar) {
    registrar.registerEndpoint(
        new JmsListenerEndpoint() {
          @Override
          public @NotNull String getId() {
            return "default-subscription-name";
          }

          @Override
          public void setupListenerContainer(@NotNull MessageListenerContainer listenerContainer) {
            AbstractMessageListenerContainer container =
                (AbstractMessageListenerContainer) listenerContainer;
            container.setDestinationName("SpringListenerJms2");
            // deliberately not calling setSubscriptionName, so that spring falls back to the
            // default subscription name derived from the listener class
            container.setupMessageListener(new DefaultSubscriptionNameListener());
          }
        });
  }
}
