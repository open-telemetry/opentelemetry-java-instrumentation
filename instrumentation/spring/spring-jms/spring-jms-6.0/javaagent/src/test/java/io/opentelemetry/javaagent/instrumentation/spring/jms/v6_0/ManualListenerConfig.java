/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.jms.v6_0;

import static io.opentelemetry.instrumentation.testing.GlobalTraceUtil.runWithSpan;

import jakarta.jms.TextMessage;
import java.util.concurrent.CompletableFuture;
import org.springframework.context.annotation.Bean;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.annotation.JmsListenerConfigurer;
import org.springframework.jms.config.JmsListenerEndpoint;
import org.springframework.jms.listener.AbstractMessageListenerContainer;
import org.springframework.jms.listener.MessageListenerContainer;
import org.springframework.jms.listener.SessionAwareMessageListener;

@EnableJms
public class ManualListenerConfig extends AbstractConfig {

  @Bean
  public JmsListenerConfigurer jmsListenerConfigurer(CompletableFuture<String> receivedMessage) {
    return registrar ->
        registrar.registerEndpoint(
            new JmsListenerEndpoint() {
              @Override
              public String getId() {
                return "testid";
              }

              @Override
              public void setupListenerContainer(MessageListenerContainer listenerContainer) {
                AbstractMessageListenerContainer container =
                    (AbstractMessageListenerContainer) listenerContainer;
                container.setDestinationName("spring-jms-listener");
                container.setupMessageListener(
                    (SessionAwareMessageListener<TextMessage>)
                        (message, session) ->
                            runWithSpan(
                                "consumer", () -> receivedMessage.complete(message.getText())));
              }
            });
  }
}
