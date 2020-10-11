/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.trace.Span.Kind.CONSUMER
import static io.opentelemetry.trace.Span.Kind.PRODUCER

import com.google.common.io.Files
import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.auto.test.asserts.TraceAssert
import io.opentelemetry.instrumentation.auto.jms.JMSTracer
import io.opentelemetry.instrumentation.auto.jms.Operation
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.trace.attributes.SemanticAttributes
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import javax.jms.Message
import javax.jms.MessageListener
import javax.jms.Session
import javax.jms.TextMessage
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
import org.hornetq.jms.client.HornetQTextMessage
import spock.lang.Shared

class JMS2Test extends AgentTestRunner {
  @Shared
  HornetQServer server
  @Shared
  String messageText = "a message"
  @Shared
  Session session

  HornetQTextMessage message = session.createTextMessage(messageText)

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
    clientSession.createQueue("jms.queue.someQueue", "jms.queue.someQueue", true)
    clientSession.createQueue("jms.topic.someTopic", "jms.topic.someTopic", true)
    clientSession.close()
    sf.close()
    serverLocator.close()

    def connectionFactory = HornetQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF,
      new TransportConfiguration(InVMConnectorFactory.name))

    def connection = connectionFactory.createConnection()
    connection.start()
    session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
    session.run()
  }

  def cleanupSpec() {
    server.stop()
  }

  def "sending a message to #destinationName #destinationType generates spans"() {
    setup:
    def producer = session.createProducer(destination)
    def consumer = session.createConsumer(destination)

    producer.send(message)

    TextMessage receivedMessage = consumer.receive()
    String messageId = receivedMessage.getJMSMessageID()

    expect:
    receivedMessage.text == messageText
    assertTraces(1) {
      trace(0, 2) {
        producerSpan(it, 0, destinationType, destinationName)
        consumerSpan(it, 1, destinationType, destinationName, messageId, span(0), Operation.receive)
      }
    }

    cleanup:
    producer.close()
    consumer.close()

    where:
    destination                      | destinationType | destinationName
    session.createQueue("someQueue") | "queue"         | "someQueue"
    session.createTopic("someTopic") | "topic"         | "someTopic"
    session.createTemporaryQueue()   | "queue"         | JMSTracer.TEMP_DESTINATION_NAME
    session.createTemporaryTopic()   | "topic"         | JMSTracer.TEMP_DESTINATION_NAME
  }

  def "sending to a MessageListener on #destinationName #destinationType generates a span"() {
    setup:
    def lock = new CountDownLatch(1)
    def messageRef = new AtomicReference<TextMessage>()
    def producer = session.createProducer(destination)
    def consumer = session.createConsumer(destination)
    consumer.setMessageListener new MessageListener() {
      @Override
      void onMessage(Message message) {
        lock.await() // ensure the producer trace is reported first.
        messageRef.set(message)
      }
    }

    producer.send(message)
    lock.countDown()

    expect:
    assertTraces(1) {
      trace(0, 2) {
        producerSpan(it, 0, destinationType, destinationName)
        consumerSpan(it, 1, destinationType, destinationName, messageRef.get().getJMSMessageID(), span(0), Operation.process)
      }
    }
    // This check needs to go after all traces have been accounted for
    messageRef.get().text == messageText

    cleanup:
    producer.close()
    consumer.close()

    where:
    destination                      | destinationType | destinationName
    session.createQueue("someQueue") | "queue"         | "someQueue"
    session.createTopic("someTopic") | "topic"         | "someTopic"
    session.createTemporaryQueue()   | "queue"         | JMSTracer.TEMP_DESTINATION_NAME
    session.createTemporaryTopic()   | "topic"         | JMSTracer.TEMP_DESTINATION_NAME
  }

  def "failing to receive message with receiveNoWait on #destinationName #destinationType works"() {
    setup:
    def consumer = session.createConsumer(destination)

    // Receive with timeout
    TextMessage receivedMessage = consumer.receiveNoWait()

    expect:
    receivedMessage == null
    assertTraces(1) {
      trace(0, 1) { // Consumer trace
        span(0) {
          hasNoParent()
          name destinationName + " receive"
          kind CONSUMER
          errored false
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "jms"
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" destinationType
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" destinationName
            "${SemanticAttributes.MESSAGING_OPERATION.key}" Operation.receive.name()
          }
        }
      }
    }

    cleanup:
    consumer.close()

    where:
    destination                      | destinationType | destinationName
    session.createQueue("someQueue") | "queue"         | "someQueue"
    session.createTopic("someTopic") | "topic"         | "someTopic"
  }

  def "failing to receive message with wait(timeout) on #destinationName #destinationType works"() {
    setup:
    def consumer = session.createConsumer(destination)

    // Receive with timeout
    TextMessage receivedMessage = consumer.receive(100)

    expect:
    receivedMessage == null
    assertTraces(1) {
      trace(0, 1) { // Consumer trace
        span(0) {
          hasNoParent()
          name destinationName + " receive"
          kind CONSUMER
          errored false
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "jms"
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" destinationType
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" destinationName
            "${SemanticAttributes.MESSAGING_OPERATION.key}" Operation.receive.name()

          }
        }
      }
    }

    cleanup:
    consumer.close()

    where:
    destination                      | destinationType | destinationName
    session.createQueue("someQueue") | "queue"         | "someQueue"
    session.createTopic("someTopic") | "topic"         | "someTopic"
  }

  static producerSpan(TraceAssert trace, int index, String destinationType, String destinationName) {
    trace.span(index) {
      name destinationName + " send"
      kind PRODUCER
      errored false
      hasNoParent()
      attributes {
        "${SemanticAttributes.MESSAGING_SYSTEM.key}" "jms"
        "${SemanticAttributes.MESSAGING_DESTINATION.key}" destinationName
        "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" destinationType
        if (destinationName == JMSTracer.TEMP_DESTINATION_NAME) {
          "${SemanticAttributes.MESSAGING_TEMP_DESTINATION.key}" true
        }
      }
    }
  }

  // passing messageId = null will verify message.id is not captured,
  // passing messageId = "" will verify message.id is captured (but won't verify anything about the value),
  // any other value for messageId will verify that message.id is captured and has that same value
  static consumerSpan(TraceAssert trace, int index, String destinationType, String destinationName, String messageId, Object parentOrLinkedSpan, Operation operation) {
    trace.span(index) {
      name destinationName + " " + operation.name()
      kind CONSUMER
      if (parentOrLinkedSpan != null) {
        childOf((SpanData) parentOrLinkedSpan)
      } else {
        hasNoParent()
      }
      errored false
      attributes {
        "${SemanticAttributes.MESSAGING_SYSTEM.key}" "jms"
        "${SemanticAttributes.MESSAGING_DESTINATION.key}" destinationName
        "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" destinationType
        "${SemanticAttributes.MESSAGING_OPERATION.key}" operation.name()
        if (messageId != null) {
          //In some tests we don't know exact messageId, so we pass "" and verify just the existence of the attribute
          "${SemanticAttributes.MESSAGING_MESSAGE_ID.key}" { it == messageId || messageId == "" }
        }
        if (destinationName == JMSTracer.TEMP_DESTINATION_NAME) {
          "${SemanticAttributes.MESSAGING_TEMP_DESTINATION.key}" true
        }
      }
    }
  }
}
