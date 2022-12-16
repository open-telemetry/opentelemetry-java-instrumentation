/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.jms.v6_0;

import jakarta.jms.ConnectionFactory;
import java.util.concurrent.CompletableFuture;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.config.JmsListenerContainerFactory;

abstract class AbstractConfig {

  @Bean
  public ConnectionFactory connectionFactory(@Value("${test.broker-url}") String artemisBrokerUrl) {
    ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(artemisBrokerUrl);
    connectionFactory.setUser("test");
    connectionFactory.setPassword("test");
    return connectionFactory;
  }

  @Bean
  public JmsListenerContainerFactory<?> jmsListenerContainerFactory(
      ConnectionFactory connectionFactory) {
    DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
    factory.setConnectionFactory(connectionFactory);
    return factory;
  }

  @Bean
  public CompletableFuture<String> receivedMessage() {
    return new CompletableFuture<>();
  }
}
