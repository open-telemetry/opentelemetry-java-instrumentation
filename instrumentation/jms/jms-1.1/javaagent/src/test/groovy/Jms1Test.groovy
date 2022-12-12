/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.apache.activemq.ActiveMQConnectionFactory
import org.apache.activemq.command.ActiveMQTextMessage
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import spock.lang.Shared
import spock.lang.Unroll

import javax.jms.Connection
import javax.jms.Message
import javax.jms.MessageListener
import javax.jms.Session
import javax.jms.TextMessage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

import static io.opentelemetry.api.trace.SpanKind.CONSUMER
import static io.opentelemetry.api.trace.SpanKind.PRODUCER

@Unroll
class Jms1Test extends AgentInstrumentationSpecification {

  private static final Logger logger = LoggerFactory.getLogger(Jms1Test)

  private static final GenericContainer broker = new GenericContainer("rmohr/activemq:latest")
    .withExposedPorts(61616, 8161)
    .withLogConsumer(new Slf4jLogConsumer(logger))

  @Shared
  String messageText = "a message"
  @Shared
  Session session

  ActiveMQTextMessage message = session.createTextMessage(messageText)

  def setupSpec() {
    broker.start()
    ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("tcp://localhost:" + broker.getMappedPort(61616))

    Connection connection = connectionFactory.createConnection()
    connection.start()
    session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
  }

  def cleanupSpec() {
    broker.stop()
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

    producer.send(message)
    lock.countDown()

    expect:
    assertTraces(1) {
      trace(0, 2) {
        producerSpan(it, 0, destinationType, destinationName)
        consumerSpan(it, 1, destinationType, destinationName, messageRef.get().getJMSMessageID(), "process", span(0))
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

  def "sending a read-only message to #destinationName #destinationType fails"() {
    setup:
    def producer = session.createProducer(destination)
    def consumer = session.createConsumer(destination)

    expect:
    !message.isReadOnlyProperties()

    when:
    message.setReadOnlyProperties(true)
    and:
    producer.send(message)

    TextMessage receivedMessage = consumer.receive() as TextMessage

    then:
    receivedMessage.text == messageText

    // This will result in a logged failure because we tried to
    // write properties in MessagePropertyTextMap when readOnlyProperties = true.
    // The consumer span will also not be linked to the parent.
    assertTraces(2) {
      trace(0, 1) {
        producerSpan(it, 0, destinationType, destinationName)
      }
      trace(1, 1) {
        consumerSpan(it, 0, destinationType, destinationName, "", "receive", null)
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

  def "capture message header as span attribute"() {
    setup:
    def destinationName = "someQueue"
    def destinationType = "queue"
    def destination = session.createQueue(destinationName)
    def producer = session.createProducer(destination)
    def consumer = session.createConsumer(destination)

    def message = session.createTextMessage(messageText)
    message.setStringProperty("test-message-header", "test")
    message.setIntProperty("test-message-int-header", 1234)
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
        producerSpan(it, 1, destinationType, destinationName, span(0), true)

        producerSpanData = span(1)
      }
      trace(1, 2) {
        span(0) {
          name "consumer parent"
          hasNoParent()
        }
        consumerSpan(it, 1, destinationType, destinationName, messageId, "receive", span(0), producerSpanData, true)
      }
    }

    cleanup:
    producer.close()
    consumer.close()
  }

  static producerSpan(TraceAssert trace, int index, String destinationType, String destinationName, SpanData parentSpan = null, boolean testHeaders = false) {
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
        "$SemanticAttributes.MESSAGING_DESTINATION" destinationName
        "$SemanticAttributes.MESSAGING_DESTINATION_KIND" destinationType
        if (destinationName == "(temporary)") {
          "$SemanticAttributes.MESSAGING_TEMP_DESTINATION" true
        }
        "$SemanticAttributes.MESSAGING_MESSAGE_ID" String
        if (testHeaders) {
          "messaging.header.test_message_header" { it == ["test"] }
          "messaging.header.test_message_int_header" { it == ["1234"] }
        }
      }
    }
  }

  // passing messageId = null will verify message.id is not captured,
  // passing messageId = "" will verify message.id is captured (but won't verify anything about the value),
  // any other value for messageId will verify that message.id is captured and has that same value
  static consumerSpan(TraceAssert trace, int index, String destinationType, String destinationName, String messageId, String operation, SpanData parentSpan, SpanData linkedSpan = null, boolean testHeaders = false) {
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
        "$SemanticAttributes.MESSAGING_DESTINATION" destinationName
        "$SemanticAttributes.MESSAGING_DESTINATION_KIND" destinationType
        "$SemanticAttributes.MESSAGING_OPERATION" operation
        if (messageId != null) {
          //In some tests we don't know exact messageId, so we pass "" and verify just the existence of the attribute
          "$SemanticAttributes.MESSAGING_MESSAGE_ID" { it == messageId || messageId == "" }
        }
        if (destinationName == "(temporary)") {
          "$SemanticAttributes.MESSAGING_TEMP_DESTINATION" true
        }
        if (testHeaders) {
          "messaging.header.test_message_header" { it == ["test"] }
          "messaging.header.test_message_int_header" { it == ["1234"] }
        }
      }
    }
  }
}
