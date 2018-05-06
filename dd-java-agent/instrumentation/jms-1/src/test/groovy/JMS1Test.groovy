import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.DDSpanTypes
import org.apache.activemq.ActiveMQConnectionFactory
import org.apache.activemq.ActiveMQMessageConsumer
import org.apache.activemq.ActiveMQMessageProducer
import org.apache.activemq.junit.EmbeddedActiveMQBroker
import spock.lang.Shared
import spock.lang.Unroll

import javax.jms.Connection
import javax.jms.Message
import javax.jms.MessageListener
import javax.jms.Session
import javax.jms.TextMessage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

class JMS1Test extends AgentTestRunner {
  @Shared
  static Session session

  def setupSpec() {
    EmbeddedActiveMQBroker broker = new EmbeddedActiveMQBroker()
    broker.start()
    final ActiveMQConnectionFactory connectionFactory = broker.createConnectionFactory()

    final Connection connection = connectionFactory.createConnection()
    connection.start()
    session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
  }

  @Unroll
  def "sending a message to #resourceName generates spans"() {
    setup:
    def producer = session.createProducer(destination)
    def consumer = session.createConsumer(destination)
    def message = session.createTextMessage("a message")

    producer.send(message)

    TextMessage receivedMessage = consumer.receive()

    expect:
    receivedMessage.text == "a message"
    TEST_WRITER.size() == 2

    and: // producer trace
    def trace = TEST_WRITER.firstTrace()
    trace.size() == 3

    and: // span 0
    def span0 = trace[0]

    span0.context().operationName == "jms.produce"
    span0.serviceName == "jms"
    span0.resourceName == "Produced for $resourceName"
    span0.type == DDSpanTypes.MESSAGE_PRODUCER
    !span0.context().getErrorFlag()
    span0.context().parentId == 0


    def tags0 = span0.context().tags
    tags0["span.kind"] == "producer"
    tags0["component"] == "jms1"

    tags0["span.origin.type"] == ActiveMQMessageProducer.name

    tags0["thread.name"] != null
    tags0["thread.id"] != null
    tags0.size() == 6

    and: // span 1
    def span1 = trace[1]

    span1.context().operationName == "jms.produce"
    span1.serviceName == "jms"
    span1.resourceName == "Produced for $resourceName"
    span1.type == DDSpanTypes.MESSAGE_PRODUCER
    !span1.context().getErrorFlag()
    span1.context().parentId == span0.context().spanId


    def tags1 = span1.context().tags
    tags1["span.kind"] == "producer"
    tags1["component"] == "jms1"

    tags1["span.origin.type"] == ActiveMQMessageProducer.name

    tags1["thread.name"] != null
    tags1["thread.id"] != null
    tags1.size() == 6

    and: // span 2
    def span2 = trace[2]

    span2.context().operationName == "jms.produce"
    span2.serviceName == "jms"
    span2.resourceName == "Produced for $resourceName"
    span2.type == DDSpanTypes.MESSAGE_PRODUCER
    !span2.context().getErrorFlag()
    span2.context().parentId == span1.context().spanId


    def tags2 = span2.context().tags
    tags2["span.kind"] == "producer"
    tags2["component"] == "jms1"

    tags2["span.origin.type"] == ActiveMQMessageProducer.name

    tags2["thread.name"] != null
    tags2["thread.id"] != null
    tags2.size() == 6

    and: // consumer trace
    def consumerTrace = TEST_WRITER.get(1)
    consumerTrace.size() == 1

    def consumerSpan = consumerTrace[0]

    consumerSpan.context().operationName == "jms.consume"
    consumerSpan.serviceName == "jms"
    consumerSpan.resourceName == "Consumed from $resourceName"
    consumerSpan.type == DDSpanTypes.MESSAGE_CONSUMER
    !consumerSpan.context().getErrorFlag()
    consumerSpan.context().parentId == span2.context().spanId


    def consumerTags = consumerSpan.context().tags
    consumerTags["span.kind"] == "consumer"
    consumerTags["component"] == "jms1"

    consumerTags["span.origin.type"] == ActiveMQMessageConsumer.name

    consumerTags["thread.name"] != null
    consumerTags["thread.id"] != null
    consumerTags.size() == 6

    cleanup:
    producer.close()
    consumer.close()

    where:
    destination                      | resourceName
    session.createQueue("someQueue") | "Queue someQueue"
    session.createTopic("someTopic") | "Topic someTopic"
    session.createTemporaryQueue()   | "Temporary Queue"
    session.createTemporaryTopic()   | "Temporary Topic"
  }

  @Unroll
  def "sending to a MessageListener on #resourceName generates a span"() {
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

    def message = session.createTextMessage("a message")
    producer.send(message)
    lock.countDown()
    TEST_WRITER.waitForTraces(2)

    expect:
    messageRef.get().text == "a message"
    TEST_WRITER.size() == 2

    and: // producer trace
    def trace = TEST_WRITER.firstTrace()
    trace.size() == 3

    and: // span 0
    def span0 = trace[0]

    span0.context().operationName == "jms.produce"
    span0.serviceName == "jms"
    span0.resourceName == "Produced for $resourceName"
    span0.type == DDSpanTypes.MESSAGE_PRODUCER
    !span0.context().getErrorFlag()
    span0.context().parentId == 0


    def tags0 = span0.context().tags
    tags0["span.kind"] == "producer"
    tags0["component"] == "jms1"

    tags0["span.origin.type"] == ActiveMQMessageProducer.name

    tags0["thread.name"] != null
    tags0["thread.id"] != null
    tags0.size() == 6

    and: // span 1
    def span1 = trace[1]

    span1.context().operationName == "jms.produce"
    span1.serviceName == "jms"
    span1.resourceName == "Produced for $resourceName"
    span1.type == DDSpanTypes.MESSAGE_PRODUCER
    !span1.context().getErrorFlag()
    span1.context().parentId == span0.context().spanId


    def tags1 = span1.context().tags
    tags1["span.kind"] == "producer"
    tags1["component"] == "jms1"

    tags1["span.origin.type"] == ActiveMQMessageProducer.name

    tags1["thread.name"] != null
    tags1["thread.id"] != null
    tags1.size() == 6

    and: // span 2
    def span2 = trace[2]

    span2.context().operationName == "jms.produce"
    span2.serviceName == "jms"
    span2.resourceName == "Produced for $resourceName"
    span2.type == DDSpanTypes.MESSAGE_PRODUCER
    !span2.context().getErrorFlag()
    span2.context().parentId == span1.context().spanId


    def tags2 = span2.context().tags
    tags2["span.kind"] == "producer"
    tags2["component"] == "jms1"

    tags2["span.origin.type"] == ActiveMQMessageProducer.name

    tags2["thread.name"] != null
    tags2["thread.id"] != null
    tags2.size() == 6

    and: // consumer trace
    def consumerTrace = TEST_WRITER.get(1)
    consumerTrace.size() == 1

    def consumerSpan = consumerTrace[0]

    consumerSpan.context().operationName == "jms.onMessage"
    consumerSpan.serviceName == "jms"
    consumerSpan.resourceName == "Received from $resourceName"
    consumerSpan.type == DDSpanTypes.MESSAGE_CONSUMER
    !consumerSpan.context().getErrorFlag()
    consumerSpan.context().parentId == span2.context().spanId


    def consumerTags = consumerSpan.context().tags
    consumerTags["span.kind"] == "consumer"
    consumerTags["component"] == "jms1"

    consumerTags["span.origin.type"] != null

    consumerTags["thread.name"] != null
    consumerTags["thread.id"] != null
    consumerTags.size() == 6

    cleanup:
    producer.close()
    consumer.close()

    where:
    destination                      | resourceName
    session.createQueue("someQueue") | "Queue someQueue"
    session.createTopic("someTopic") | "Topic someTopic"
    session.createTemporaryQueue()   | "Temporary Queue"
    session.createTemporaryTopic()   | "Temporary Topic"
  }
}
