/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package listener


import org.springframework.jms.annotation.EnableJms
import org.springframework.jms.annotation.JmsListenerConfigurer
import org.springframework.jms.config.JmsListenerEndpoint
import org.springframework.jms.config.JmsListenerEndpointRegistrar
import org.springframework.jms.listener.AbstractMessageListenerContainer
import org.springframework.jms.listener.MessageListenerContainer
import org.springframework.jms.listener.SessionAwareMessageListener

import javax.jms.JMSException
import javax.jms.Message
import javax.jms.Session

@EnableJms
class ManualListenerConfig extends AbstractConfig implements JmsListenerConfigurer {

  @Override
  void configureJmsListeners(JmsListenerEndpointRegistrar registrar) {
    registrar.registerEndpoint(new JmsListenerEndpoint() {
      @Override
      String getId() {
        return "testid"
      }

      @Override
      void setupListenerContainer(MessageListenerContainer listenerContainer) {
        var container = (AbstractMessageListenerContainer) listenerContainer
        container.setDestinationName("SpringListenerJms2")
        container.setupMessageListener(new SessionAwareMessageListener<Message>() {
          @Override
          void onMessage(Message message, Session session) throws JMSException {
            println "received: " + message
          }
        })
      }
    })
  }
}
