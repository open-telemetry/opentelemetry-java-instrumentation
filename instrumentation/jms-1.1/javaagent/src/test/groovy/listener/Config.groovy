/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package listener

import org.apache.activemq.ActiveMQConnectionFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.jms.annotation.EnableJms
import org.springframework.jms.config.DefaultJmsListenerContainerFactory
import org.springframework.jms.config.JmsListenerContainerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait

import javax.annotation.PreDestroy
import javax.jms.ConnectionFactory
import java.time.Duration

@Configuration
@ComponentScan
@EnableJms
class Config {

  private static GenericContainer broker = new GenericContainer("rmohr/activemq:latest")
    .withExposedPorts(61616, 8161)
    .waitingFor(Wait.forLogMessage(".*Apache ActiveMQ .* started.*", 1))
    .withStartupTimeout(Duration.ofMinutes(2))

  static {
    broker.start()
  }

  @Bean
  ConnectionFactory connectionFactory() {
    return new ActiveMQConnectionFactory("tcp://localhost:" + broker.getMappedPort(61616))
  }

  @Bean
  JmsListenerContainerFactory<?> containerFactory(ConnectionFactory connectionFactory) {
    DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory()
    factory.setConnectionFactory(connectionFactory)
    return factory
  }

  @PreDestroy
  void destroy() {
    broker.stop()
  }
}
