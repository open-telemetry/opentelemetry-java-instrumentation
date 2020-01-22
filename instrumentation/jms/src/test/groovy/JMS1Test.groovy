import io.opentelemetry.auto.api.MoreTags
import io.opentelemetry.auto.api.SpanTypes
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.auto.test.asserts.TraceAssert
import io.opentelemetry.sdk.trace.SpanData
import org.apache.activemq.ActiveMQConnectionFactory
import org.apache.activemq.ActiveMQMessageConsumer
import org.apache.activemq.ActiveMQMessageProducer
import org.apache.activemq.command.ActiveMQTextMessage
import org.apache.activemq.junit.EmbeddedActiveMQBroker
import spock.lang.Shared

import javax.jms.Connection
import javax.jms.Message
import javax.jms.MessageListener
import javax.jms.Session
import javax.jms.TextMessage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

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
    final ActiveMQConnectionFactory connectionFactory = broker.createConnectionFactory()

    final Connection connection = connectionFactory.createConnection()
    connection.start()
    session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
  }

  def cleanupSpec() {
    broker.stop()
  }

  def "sending a message to #jmsResourceName generates spans"() {
    setup:
    def producer = session.createProducer(destination)
    def consumer = session.createConsumer(destination)

    producer.send(message)

    TextMessage receivedMessage = consumer.receive()

    expect:
    receivedMessage.text == messageText
    assertTraces(1) {
      trace(0, 2) {
        producerSpan(it, 0, jmsResourceName)
        consumerSpan(it, 1, jmsResourceName, false, ActiveMQMessageConsumer, span(0))
      }
    }

    cleanup:
    producer.close()
    consumer.close()

    where:
    destination                      | jmsResourceName
    session.createQueue("someQueue") | "Queue someQueue"
    session.createTopic("someTopic") | "Topic someTopic"
    session.createTemporaryQueue()   | "Temporary Queue"
    session.createTemporaryTopic()   | "Temporary Topic"
  }

  def "sending to a MessageListener on #jmsResourceName generates a span"() {
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
        producerSpan(it, 0, jmsResourceName)
        consumerSpan(it, 1, jmsResourceName, true, consumer.messageListener.class, span(0))
      }
    }
    // This check needs to go after all traces have been accounted for
    messageRef.get().text == messageText

    cleanup:
    producer.close()
    consumer.close()

    where:
    destination                      | jmsResourceName
    session.createQueue("someQueue") | "Queue someQueue"
    session.createTopic("someTopic") | "Topic someTopic"
    session.createTemporaryQueue()   | "Temporary Queue"
    session.createTemporaryTopic()   | "Temporary Topic"
  }

  def "failing to receive message with receiveNoWait on #jmsResourceName works"() {
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
          operationName "jms.consume"
          errored false

          tags {
            "$MoreTags.SERVICE_NAME" "jms"
            "$MoreTags.RESOURCE_NAME" "JMS receiveNoWait"
            "$MoreTags.SPAN_TYPE" SpanTypes.MESSAGE_CONSUMER
            "$Tags.COMPONENT" "jms"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CONSUMER
            "span.origin.type" ActiveMQMessageConsumer.name
          }
        }
      }
    }

    cleanup:
    consumer.close()

    where:
    destination                      | jmsResourceName
    session.createQueue("someQueue") | "Queue someQueue"
    session.createTopic("someTopic") | "Topic someTopic"
  }

  def "failing to receive message with wait(timeout) on #jmsResourceName works"() {
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
          operationName "jms.consume"
          errored false

          tags {
            "$MoreTags.SERVICE_NAME" "jms"
            "$MoreTags.RESOURCE_NAME" "JMS receive"
            "$MoreTags.SPAN_TYPE" SpanTypes.MESSAGE_CONSUMER
            "$Tags.COMPONENT" "jms"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CONSUMER
            "span.origin.type" ActiveMQMessageConsumer.name
          }
        }
      }
    }

    cleanup:
    consumer.close()

    where:
    destination                      | jmsResourceName
    session.createQueue("someQueue") | "Queue someQueue"
    session.createTopic("someTopic") | "Topic someTopic"
  }

  def "sending a read-only message to #jmsResourceName fails"() {
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
        producerSpan(it, 0, jmsResourceName)
      }
      trace(1, 1) {
        span(0) {
          parent()
          operationName "jms.consume"
          errored false

          tags {
            "$MoreTags.SERVICE_NAME" "jms"
            "$MoreTags.RESOURCE_NAME" "Consumed from $jmsResourceName"
            "$MoreTags.SPAN_TYPE" SpanTypes.MESSAGE_CONSUMER
            "$Tags.COMPONENT" "jms"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CONSUMER
            "span.origin.type" ActiveMQMessageConsumer.name
          }
        }
      }
    }

    cleanup:
    producer.close()
    consumer.close()

    where:
    destination                      | jmsResourceName
    session.createQueue("someQueue") | "Queue someQueue"
    session.createTopic("someTopic") | "Topic someTopic"
    session.createTemporaryQueue()   | "Temporary Queue"
    session.createTemporaryTopic()   | "Temporary Topic"
  }

  static producerSpan(TraceAssert trace, int index, String jmsResourceName) {
    trace.span(index) {
      operationName "jms.produce"
      errored false
      parent()

      tags {
        "$MoreTags.SERVICE_NAME" "jms"
        "$MoreTags.RESOURCE_NAME" "Produced for $jmsResourceName"
        "$MoreTags.SPAN_TYPE" SpanTypes.MESSAGE_PRODUCER
        "$Tags.COMPONENT" "jms"
        "$Tags.SPAN_KIND" Tags.SPAN_KIND_PRODUCER
        "span.origin.type" ActiveMQMessageProducer.name
      }
    }
  }

  static consumerSpan(TraceAssert trace, int index, String jmsResourceName, boolean messageListener, Class origin, Object parentSpan) {
    trace.span(index) {
      if (messageListener) {
        operationName "jms.onMessage"
      } else {
        operationName "jms.consume"
      }
      errored false
      childOf((SpanData) parentSpan)

      tags {
        "$MoreTags.SERVICE_NAME" "jms"
        "$MoreTags.RESOURCE_NAME" messageListener ? "Received from $jmsResourceName" : "Consumed from $jmsResourceName"
        "$MoreTags.SPAN_TYPE" SpanTypes.MESSAGE_CONSUMER
        "$Tags.COMPONENT" "jms"
        "$Tags.SPAN_KIND" Tags.SPAN_KIND_CONSUMER
        "span.origin.type" origin.name
      }
    }
  }
}
