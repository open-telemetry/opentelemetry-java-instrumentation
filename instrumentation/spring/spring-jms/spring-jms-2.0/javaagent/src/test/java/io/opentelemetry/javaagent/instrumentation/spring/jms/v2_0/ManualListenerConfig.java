/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.jms.v2_0;

import ch.qos.logback.classic.Level;
import io.opentelemetry.instrumentation.test.utils.LoggerUtils;
import javax.jms.Message;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.annotation.JmsListenerConfigurer;
import org.springframework.jms.config.JmsListenerEndpoint;
import org.springframework.jms.config.JmsListenerEndpointRegistrar;
import org.springframework.jms.listener.AbstractMessageListenerContainer;
import org.springframework.jms.listener.MessageListenerContainer;
import org.springframework.jms.listener.SessionAwareMessageListener;

@EnableJms
public class ManualListenerConfig extends AbstractConfig implements JmsListenerConfigurer {

  private static final Logger logger = LoggerFactory.getLogger(ManualListenerConfig.class);

  static {
    LoggerUtils.setLevel(logger, Level.INFO);
  }

  @Override
  public void configureJmsListeners(JmsListenerEndpointRegistrar registrar) {
    registrar.registerEndpoint(
        new JmsListenerEndpoint() {
          @Override
          public @NotNull String getId() {
            return "testid";
          }

          @Override
          public void setupListenerContainer(@NotNull MessageListenerContainer listenerContainer) {
            AbstractMessageListenerContainer container =
                (AbstractMessageListenerContainer) listenerContainer;
            container.setDestinationName("SpringListenerJms2");
            container.setupMessageListener(
                (SessionAwareMessageListener<Message>)
                    (message, session) -> logger.info("received: {}", message));
          }
        });
  }
}
