/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.trace.Span.Kind.CLIENT
import static io.opentelemetry.trace.Span.Kind.CONSUMER
import static io.opentelemetry.trace.Span.Kind.PRODUCER

import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.auto.test.asserts.TraceAssert
import io.opentelemetry.auto.test.utils.ConfigUtils
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
import org.apache.activemq.ActiveMQMessageConsumer
import org.apache.activemq.command.ActiveMQTextMessage
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import spock.lang.Requires
import spock.lang.Shared

@Requires({"true" != System.getenv("CIRCLECI")})
class JMS1Test extends AgentTestRunner {

  private static final Logger logger = LoggerFactory.getLogger(JMS1Test)

  public static GenericContainer activemq = new GenericContainer("rmohr/activemq")
    .withExposedPorts(61616, 8161)
    .withLogConsumer(new Slf4jLogConsumer(logger))

  @Shared
  String messageText = "a message"
  @Shared
  Session session

  ActiveMQTextMessage message = session.createTextMessage(messageText)

  static {
    ConfigUtils.updateConfig {
      System.setProperty("otel.trace.classes.exclude", "org.springframework.jms.config.JmsListenerEndpointRegistry\$AggregatingCallback,org.springframework.context.support.DefaultLifecycleProcessor\$1")
    }
  }

  def setupSpec() {
    activemq.start()
    ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("tcp://localhost:" + activemq.getMappedPort(61616))

    Connection connection = connectionFactory.createConnection()
    connection.start()
    session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
  }

  def cleanupSpec() {
    activemq.stop()
    ConfigUtils.updateConfig {
      System.clearProperty("otel.trace.classes.exclude")
    }
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
    assertTraces(2) {
      trace(0, 1) {
        producerSpan(it, 0, destinationType, destinationName)
      }
      trace(1, 1) {
        consumerSpan(it, 0, destinationType, destinationName, messageId, false, ActiveMQMessageConsumer, traces[0][0])
      }
    }

    cleanup:
    producer.close()
    consumer.close()

    where:
    destination                      | destinationType | destinationName
    session.createQueue("someQueue") | "queue"         | "someQueue"
    session.createTopic("someTopic") | "topic"         | "someTopic"
    session.createTemporaryQueue()   | "queue"         | "<temporary>"
    session.createTemporaryTopic()   | "topic"         | "<temporary>"
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
        consumerSpan(it, 1, destinationType, destinationName, messageRef.get().getJMSMessageID(), true, consumer.messageListener.class, span(0))
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
    session.createTemporaryQueue()   | "queue"         | "<temporary>"
    session.createTemporaryTopic()   | "topic"         | "<temporary>"
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
          name destinationType + "/" + destinationName + " receive"
          kind CLIENT
          errored false
          attributes {
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key()}" destinationType
            "${SemanticAttributes.MESSAGING_DESTINATION.key()}" destinationName
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
          name destinationType + "/" + destinationName + " receive"
          kind CLIENT
          errored false
          attributes {
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key()}" destinationType
            "${SemanticAttributes.MESSAGING_DESTINATION.key()}" destinationName
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
          hasNoParent()
          name destinationType + "/" + destinationName + " receive"
          kind CLIENT
          errored false
          attributes {
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key()}" destinationType
            "${SemanticAttributes.MESSAGING_DESTINATION.key()}" destinationName
            "${SemanticAttributes.MESSAGING_MESSAGE_ID.key()}" receivedMessage.getJMSMessageID()
            if (destinationName == "<temporary>") {
              "${SemanticAttributes.MESSAGING_TEMP_DESTINATION.key()}" true
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
    session.createTemporaryQueue()   | "queue"         | "<temporary>"
    session.createTemporaryTopic()   | "topic"         | "<temporary>"
  }

  static producerSpan(TraceAssert trace, int index, String destinationType, String destinationName) {
    trace.span(index) {
      name destinationType + "/" + destinationName + " send"
      kind PRODUCER
      errored false
      hasNoParent()
      attributes {
        "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key()}" destinationType
        "${SemanticAttributes.MESSAGING_DESTINATION.key()}" destinationName
        if (destinationName == "<temporary>") {
          "${SemanticAttributes.MESSAGING_TEMP_DESTINATION.key()}" true
        }
      }
    }
  }

  static consumerSpan(TraceAssert trace, int index, String destinationType, String destinationName, String messageId, boolean messageListener, Class origin, Object parentOrLinkedSpan) {
    trace.span(index) {
      name destinationType + "/" + destinationName + " receive"
      if (messageListener) {
        kind CONSUMER
        childOf((SpanData) parentOrLinkedSpan)
      } else {
        kind CLIENT
        hasNoParent()
        hasLink((SpanData) parentOrLinkedSpan)
      }
      errored false
      attributes {
        "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key()}" destinationType
        "${SemanticAttributes.MESSAGING_DESTINATION.key()}" destinationName
        if (messageId != null) {
          "${SemanticAttributes.MESSAGING_MESSAGE_ID.key()}" messageId
        } else {
          "${SemanticAttributes.MESSAGING_MESSAGE_ID.key()}" String
        }
        if (destinationName == "<temporary>") {
          "${SemanticAttributes.MESSAGING_TEMP_DESTINATION.key()}" true
        }
      }
    }
  }
}
