/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.jms.v2_0;

import java.io.File;
import java.nio.file.Files;
import java.util.Collections;
import javax.annotation.PreDestroy;
import javax.jms.ConnectionFactory;
import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.api.core.client.ClientSession;
import org.hornetq.api.core.client.ClientSessionFactory;
import org.hornetq.api.core.client.HornetQClient;
import org.hornetq.api.core.client.ServerLocator;
import org.hornetq.api.jms.HornetQJMSClient;
import org.hornetq.api.jms.JMSFactoryType;
import org.hornetq.core.config.Configuration;
import org.hornetq.core.config.CoreQueueConfiguration;
import org.hornetq.core.config.impl.ConfigurationImpl;
import org.hornetq.core.remoting.impl.invm.InVMAcceptorFactory;
import org.hornetq.core.remoting.impl.invm.InVMConnectorFactory;
import org.hornetq.core.server.HornetQServer;
import org.hornetq.core.server.HornetQServers;
import org.springframework.context.annotation.Bean;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.config.JmsListenerContainerFactory;

abstract class AbstractConfig {

  private HornetQServer server;

  @Bean
  ConnectionFactory connectionFactory() throws Exception {
    File tempDir = Files.createTempDirectory("tmp").toFile();
    tempDir.deleteOnExit();

    Configuration config = new ConfigurationImpl();
    config.setBindingsDirectory(tempDir.getPath());
    config.setJournalDirectory(tempDir.getPath());
    config.setCreateBindingsDir(false);
    config.setCreateJournalDir(false);
    config.setSecurityEnabled(false);
    config.setPersistenceEnabled(false);
    config.setQueueConfigurations(
        Collections.singletonList(
            new CoreQueueConfiguration("someQueue", "someQueue", null, true)));
    config.setAcceptorConfigurations(
        Collections.singleton(new TransportConfiguration(InVMAcceptorFactory.class.getName())));

    server = HornetQServers.newHornetQServer(config);
    server.start();

    ServerLocator serverLocator =
        HornetQClient.createServerLocatorWithoutHA(
            new TransportConfiguration(InVMConnectorFactory.class.getName()));
    ClientSessionFactory sf = serverLocator.createSessionFactory();
    ClientSession clientSession = sf.createSession(false, false, false);
    clientSession.createQueue("jms.queue.SpringListenerJms2", "jms.queue.SpringListenerJms2", true);
    clientSession.close();
    sf.close();
    serverLocator.close();

    return HornetQJMSClient.createConnectionFactoryWithoutHA(
        JMSFactoryType.CF, new TransportConfiguration(InVMConnectorFactory.class.getName()));
  }

  @Bean
  JmsListenerContainerFactory<?> jmsListenerContainerFactory(ConnectionFactory connectionFactory) {
    DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
    factory.setConnectionFactory(connectionFactory);
    return factory;
  }

  @PreDestroy
  void destroy() throws Exception {
    server.stop();
  }
}
