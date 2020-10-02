/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import static io.opentelemetry.trace.Span.Kind.CONSUMER
import static io.opentelemetry.trace.Span.Kind.PRODUCER

import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.auto.test.asserts.TraceAssert
import io.opentelemetry.instrumentation.auto.jms.JMSTracer
import io.opentelemetry.instrumentation.auto.jms.Operation
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.trace.attributes.SemanticAttributes
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import javax.jms.Connection
import javax.jms.Message
import javax.jms.MessageListener
import javax.jms.Session
import javax.jms.TextMessage
import org.apache.activemq.ActiveMQConnectionFactory
import org.apache.activemq.command.ActiveMQTextMessage
import org.apache.activemq.junit.EmbeddedActiveMQBroker
import spock.lang.Shared

class JMS1Test extends AgentTestRunner {
  @Shared
  EmbeddedActiveMQBroker broker = new EmbeddedActiveMQBroker()
  @Shared
  String messageText = "a message"
  @Shared
  Session session

  ActiveMQTextMessage message = session.createTextMessage(messageText)

  def setupSpec() {
    broker.start()
    ActiveMQConnectionFactory connectionFactory = broker.createConnectionFactory()

    Connection connection = connectionFactory.createConnection()
    connection.start()
    session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
  }

  def cleanupSpec() {
    broker.stop()
  }

  def "sending a message to #expectedSpanName generates spans"() {
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

  def "sending to a MessageListener on #expectedSpanName generates a span"() {
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

  def "failing to receive message with receiveNoWait on #expectedSpanName works"() {
    setup:
    def consumer = session.createConsumer(destination)

    // Receive with timeout
    TextMessage receivedMessage = consumer.receiveNoWait()

    expect:
    receivedMessage == null
    assertTraces(1) {
      trace(0, 1) { // Consumer trace
        span(0) {
          parent()
          operationName destinationName + " receive"
          spanKind CONSUMER
          errored false
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "jms"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" destinationName
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" destinationType
            "${SemanticAttributes.MESSAGING_OPERATION.key}" "receive"
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

  def "failing to receive message with wait(timeout) on #expectedSpanName works"() {
    setup:
    def consumer = session.createConsumer(destination)

    // Receive with timeout
    TextMessage receivedMessage = consumer.receive(100)

    expect:
    receivedMessage == null
    assertTraces(1) {
      trace(0, 1) { // Consumer trace
        span(0) {
          parent()
          operationName destinationName + " receive"
          spanKind CONSUMER
          errored false
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "jms"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" destinationName
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" destinationType
            "${SemanticAttributes.MESSAGING_OPERATION.key}" "receive"
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

  def "sending a read-only message to #expectedSpanName fails"() {
    setup:
    def producer = session.createProducer(destination)
    def consumer = session.createConsumer(destination)

    expect:
    !message.isReadOnlyProperties()

    when:
    message.setReadOnlyProperties(true)
    and:
    producer.send(message)

    TextMessage receivedMessage = consumer.receive()

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
        span(0) {
          parent()
          operationName destinationName + " receive"
          spanKind CONSUMER
          errored false
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "jms"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" destinationName
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" destinationType
            "${SemanticAttributes.MESSAGING_MESSAGE_ID.key}" receivedMessage.getJMSMessageID()
            "${SemanticAttributes.MESSAGING_OPERATION.key}" "receive"
            if (destinationName == JMSTracer.TEMP_DESTINATION_NAME) {
              "${SemanticAttributes.MESSAGING_TEMP_DESTINATION.key}" true
            }
          }
        }
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

  static producerSpan(TraceAssert trace, int index, String destinationType, String destinationName) {
    trace.span(index) {
      operationName destinationName + " send"
      spanKind PRODUCER
      errored false
      parent()
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
      operationName destinationName + " " + operation.name()
      spanKind CONSUMER
      if (parentOrLinkedSpan != null) {
        childOf((SpanData) parentOrLinkedSpan)
      } else {
        parent()
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
