/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
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

import javax.jms.Message
import javax.jms.MessageListener
import javax.jms.Session
import javax.jms.TextMessage
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

import static io.opentelemetry.api.trace.SpanKind.CONSUMER
import static io.opentelemetry.api.trace.SpanKind.PRODUCER

class Jms2Test extends AgentInstrumentationSpecification {
  @Shared
  HornetQServer server
  @Shared
  String messageText = "a message"
  @Shared
  Session session

  HornetQTextMessage message = session.createTextMessage(messageText)

  def setupSpec() {
    def tempDir = Files.createTempDirectory("jmsTempDir").toFile()
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

    runWithSpan("producer parent") {
      producer.send(message)
    }

    TextMessage receivedMessage = runWithSpan("consumer parent") {
      return consumer.receive() as TextMessage
    }
    String messageId = receivedMessage.getJMSMessageID()

    expect:
    receivedMessage.text == messageText
    assertTraces(2) {
      SpanData producerSpanData
      trace(0, 2) {
        span(0) {
          name "producer parent"
          hasNoParent()
        }
        producerSpan(it, 1, destinationType, destinationName, span(0))

        producerSpanData = span(1)
      }
      trace(1, 2) {
        span(0) {
          name "consumer parent"
          hasNoParent()
        }
        consumerSpan(it, 1, destinationType, destinationName, messageId, "receive", span(0), producerSpanData)
      }
    }

    cleanup:
    producer.close()
    consumer.close()

    where:
    destination                      | destinationType | destinationName
    session.createQueue("someQueue") | "queue"         | "someQueue"
    session.createTopic("someTopic") | "topic"         | "someTopic"
    session.createTemporaryQueue()   | "queue"         | "(temporary)"
    session.createTemporaryTopic()   | "topic"         | "(temporary)"
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
        messageRef.set(message as TextMessage)
      }
    }

    runWithSpan("parent") {
      producer.send(message)
    }
    lock.countDown()

    expect:
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "parent"
          hasNoParent()
        }
        producerSpan(it, 1, destinationType, destinationName, span(0))
        consumerSpan(it, 2, destinationType, destinationName, messageRef.get().getJMSMessageID(), "process", span(1))
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
    session.createTemporaryQueue()   | "queue"         | "(temporary)"
    session.createTemporaryTopic()   | "topic"         | "(temporary)"
  }

  def "failing to receive message with receiveNoWait on #destinationName #destinationType works"() {
    setup:
    def consumer = session.createConsumer(destination)

    // Receive with timeout
    Message receivedMessage = consumer.receiveNoWait()

    expect:
    receivedMessage == null
    // span is not created if no message is received
    assertTraces(0) {}

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
    Message receivedMessage = consumer.receive(100)

    expect:
    receivedMessage == null
    // span is not created if no message is received
    assertTraces(0) {}

    cleanup:
    consumer.close()

    where:
    destination                      | destinationType | destinationName
    session.createQueue("someQueue") | "queue"         | "someQueue"
    session.createTopic("someTopic") | "topic"         | "someTopic"
  }

  def "sending a message to #destinationName #destinationType with explicit destination propagates context"() {
    given:
    def producer = session.createProducer(null)
    def consumer = session.createConsumer(destination)

    def lock = new CountDownLatch(1)
    def messageRef = new AtomicReference<TextMessage>()
    consumer.setMessageListener new MessageListener() {
      @Override
      void onMessage(Message message) {
        lock.await() // ensure the producer trace is reported first.
        messageRef.set(message as TextMessage)
      }
    }

    when:
    runWithSpan("parent") {
      producer.send(destination, message)
    }
    lock.countDown()

    then:
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "parent"
          hasNoParent()
        }
        producerSpan(it, 1, destinationType, destinationName, span(0))
        consumerSpan(it, 2, destinationType, destinationName, messageRef.get().getJMSMessageID(), "process", span(1))
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
    session.createTemporaryQueue()   | "queue"         | "(temporary)"
    session.createTemporaryTopic()   | "topic"         | "(temporary)"
  }

  static producerSpan(TraceAssert trace, int index, String destinationType, String destinationName, SpanData parentSpan = null) {
    trace.span(index) {
      name destinationName + " send"
      kind PRODUCER
      if (parentSpan == null) {
        hasNoParent()
      } else {
        childOf(parentSpan)
      }
      attributes {
        "$SemanticAttributes.MESSAGING_SYSTEM" "jms"
        "$SemanticAttributes.MESSAGING_DESTINATION_NAME" destinationName
        "$SemanticAttributes.MESSAGING_DESTINATION_KIND" destinationType
        if (destinationName == "(temporary)") {
          "$SemanticAttributes.MESSAGING_TEMP_DESTINATION" true
        }
        "$SemanticAttributes.MESSAGING_MESSAGE_ID" String
      }
    }
  }

  // passing messageId = null will verify message.id is not captured,
  // passing messageId = "" will verify message.id is captured (but won't verify anything about the value),
  // any other value for messageId will verify that message.id is captured and has that same value
  static consumerSpan(TraceAssert trace, int index, String destinationType, String destinationName, String messageId, String operation, SpanData parentSpan, SpanData linkedSpan = null) {
    trace.span(index) {
      name destinationName + " " + operation
      kind CONSUMER
      if (parentSpan == null) {
        hasNoParent()
      } else {
        childOf(parentSpan)
      }
      if (linkedSpan == null) {
        hasNoLinks()
      } else {
        hasLink(linkedSpan)
      }
      attributes {
        "$SemanticAttributes.MESSAGING_SYSTEM" "jms"
        "$SemanticAttributes.MESSAGING_DESTINATION_NAME" destinationName
        "$SemanticAttributes.MESSAGING_DESTINATION_KIND" destinationType
        "$SemanticAttributes.MESSAGING_OPERATION" operation
        if (messageId != null) {
          //In some tests we don't know exact messageId, so we pass "" and verify just the existence of the attribute
          "$SemanticAttributes.MESSAGING_MESSAGE_ID" { it == messageId || messageId == "" }
        }
        if (destinationName == "(temporary)") {
          "$SemanticAttributes.MESSAGING_TEMP_DESTINATION" true
        }
      }
    }
  }
}
