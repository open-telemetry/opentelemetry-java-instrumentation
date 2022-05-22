/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import com.google.common.io.Files
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import javax.jms.Connection
import org.hornetq.api.core.TransportConfiguration
import org.hornetq.api.core.client.HornetQClient
import org.hornetq.api.jms.HornetQJMSClient
import org.hornetq.api.jms.JMSFactoryType
import org.hornetq.core.config.Configuration
import org.hornetq.core.config.CoreQueueConfiguration
import org.hornetq.core.config.impl.ConfigurationImpl
import org.hornetq.core.remoting.impl.invm.InVMAcceptorFactory
import org.hornetq.core.remoting.impl.invm.InVMConnectorFactory
import org.hornetq.core.server.HornetQServer
import org.hornetq.core.server.HornetQServers
import org.springframework.jms.core.JmsTemplate
import spock.lang.Shared

import javax.jms.Session
import javax.jms.TextMessage
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

import static Jms2Test.consumerSpan
import static Jms2Test.producerSpan

class SpringTemplateJms2Test extends AgentInstrumentationSpecification {
  @Shared
  HornetQServer server
  @Shared
  String messageText = "a message"
  @Shared
  JmsTemplate template
  @Shared
  Session session
  @Shared
  Connection connection

  def setupSpec() {
    def tempDir = Files.createTempDir()
    tempDir.deleteOnExit()

    Configuration config = new ConfigurationImpl()
    config.bindingsDirectory = tempDir.path
    config.journalDirectory = tempDir.path
    config.createBindingsDir = false
    config.createJournalDir = false
    config.securityEnabled = false
    config.persistenceEnabled = false
    config.setQueueConfigurations([new CoreQueueConfiguration("someQueue", "someQueue", null, true)])
    config.setAcceptorConfigurations([new TransportConfiguration(InVMAcceptorFactory.name)].toSet())

    server = HornetQServers.newHornetQServer(config)
    server.start()

    def serverLocator = HornetQClient.createServerLocatorWithoutHA(new TransportConfiguration(InVMConnectorFactory.name))
    def sf = serverLocator.createSessionFactory()
    def clientSession = sf.createSession(false, false, false)
    clientSession.createQueue("jms.queue.SpringTemplateJms2", "jms.queue.SpringTemplateJms2", true)
    clientSession.close()
    sf.close()
    serverLocator.close()

    def connectionFactory = HornetQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF,
      new TransportConfiguration(InVMConnectorFactory.name))

    connection = connectionFactory.createConnection()
    connection.start()
    session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
    session.run()

    template = new JmsTemplate(connectionFactory)
    template.receiveTimeout = TimeUnit.SECONDS.toMillis(10)
  }

  def cleanupSpec() {
    session.close()
    connection.close()
    server.stop()
  }

  def "sending a message to #destinationName generates spans"() {
    setup:
    template.convertAndSend(destination, messageText)
    TextMessage receivedMessage = template.receive(destination)

    expect:
    receivedMessage.text == messageText
    assertTraces(2) {
      trace(0, 1) {
        producerSpan(it, 0, destinationType, destinationName)
      }
      trace(1, 1) {
        consumerSpan(it, 0, destinationType, destinationName, receivedMessage.getJMSMessageID(), null, "receive")
      }
    }

    where:
    destination                               | destinationType | destinationName
    session.createQueue("SpringTemplateJms2") | "queue"         | "SpringTemplateJms2"
  }

  def "send and receive message generates spans"() {
    setup:
    AtomicReference<String> msgId = new AtomicReference<>()
    Thread.start {
      TextMessage msg = template.receive(destination)
      assert msg.text == messageText
      msgId.set(msg.getJMSMessageID())

      // There's a chance this might be reported last, messing up the assertion.
      template.send(msg.getJMSReplyTo()) {
        session -> template.getMessageConverter().toMessage("responded!", session)
      }
    }
    TextMessage receivedMessage = template.sendAndReceive(destination) {
      session -> template.getMessageConverter().toMessage(messageText, session)
    }

    expect:
    receivedMessage.text == "responded!"
    assertTraces(4) {
      traces.sort(orderByRootSpanName(
        "$destinationName receive",
        "$destinationName send",
        "(temporary) receive",
        "(temporary) send"))

      trace(0, 1) {
        consumerSpan(it, 0, destinationType, destinationName, msgId.get(), null, "receive")
      }
      trace(1, 1) {
        producerSpan(it, 0, destinationType, destinationName)
      }
      trace(2, 1) {
        consumerSpan(it, 0, "queue", "(temporary)", receivedMessage.getJMSMessageID(), null, "receive")
      }
      trace(3, 1) {
        producerSpan(it, 0, "queue", "(temporary)")
      }
    }

    where:
    destination                               | destinationType | destinationName
    session.createQueue("SpringTemplateJms2") | "queue"         | "SpringTemplateJms2"
  }
}
