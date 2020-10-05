/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package listener

import javax.annotation.PreDestroy
import javax.jms.ConnectionFactory
import org.apache.activemq.junit.EmbeddedActiveMQBroker
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.jms.annotation.EnableJms
import org.springframework.jms.config.DefaultJmsListenerContainerFactory
import org.springframework.jms.config.JmsListenerContainerFactory

@Configuration
@ComponentScan
@EnableJms
class Config {

  @Bean
  EmbeddedActiveMQBroker broker() {
    def broker = new EmbeddedActiveMQBroker()
    broker.start()
    return broker
  }

  @Bean
  ConnectionFactory connectionFactory(EmbeddedActiveMQBroker broker) {
    return broker.createConnectionFactory()
  }

  @Bean
  JmsListenerContainerFactory<?> containerFactory(ConnectionFactory connectionFactory) {
    DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory()
    factory.setConnectionFactory(connectionFactory)
    return factory
  }

  @PreDestroy
  void destroy() {
    broker().stop()
  }
}
