import datadog.trace.agent.test.AgentTestRunner
import org.apache.activemq.ActiveMQConnectionFactory
import org.apache.activemq.ActiveMQMessageConsumer
import org.apache.activemq.junit.EmbeddedActiveMQBroker
import org.springframework.jms.core.JmsTemplate
import spock.lang.Shared

import javax.jms.Connection
import javax.jms.Session
import javax.jms.TextMessage
import java.util.concurrent.TimeUnit

import static JMS1Test.consumerTrace
import static JMS1Test.producerTrace

class SpringTemplateJMS1Test extends AgentTestRunner {
  @Shared
  EmbeddedActiveMQBroker broker = new EmbeddedActiveMQBroker()
  @Shared
  String messageText = "a message"
  @Shared
  JmsTemplate template
  @Shared
  Session session

  def setupSpec() {
    broker.start()
    final ActiveMQConnectionFactory connectionFactory = broker.createConnectionFactory()
    final Connection connection = connectionFactory.createConnection()
    connection.start()
    session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)

    template = new JmsTemplate(connectionFactory)
    // Make this longer than timeout on TEST_WRITER.waitForTraces
    // Otherwise caller might give up waiting before callee has a chance to respond.
    template.receiveTimeout = TimeUnit.SECONDS.toMillis(21)
  }

  def cleanupSpec() {
    broker.stop()
  }

  def "sending a message to #jmsResourceName generates spans"() {
    setup:
    template.convertAndSend(destination, messageText)
    TextMessage receivedMessage = template.receive(destination)

    expect:
    receivedMessage.text == messageText
    assertTraces(2) {
      producerTrace(it, 0, jmsResourceName)
      consumerTrace(it, 1, jmsResourceName, false, ActiveMQMessageConsumer)
    }

    where:
    destination                            | jmsResourceName
    session.createQueue("someSpringQueue") | "Queue someSpringQueue"
  }

  def "send and receive message generates spans"() {
    setup:
    Thread.start {
      TextMessage msg = template.receive(destination)
      assert msg.text == messageText

      // Make sure that first pair of send/receive traces has landed to simplify assertions
      TEST_WRITER.waitForTraces(2)

      template.send(msg.getJMSReplyTo()) {
        session -> template.getMessageConverter().toMessage("responded!", session)
      }
    }
    TextMessage receivedMessage = template.sendAndReceive(destination) {
      session -> template.getMessageConverter().toMessage(messageText, session)
    }

    TEST_WRITER.waitForTraces(4)
    // Manually reorder if reported in the wrong order.
    if (TEST_WRITER[1][0].operationName == "jms.produce") {
      def producerTrace = TEST_WRITER[1]
      TEST_WRITER[1] = TEST_WRITER[0]
      TEST_WRITER[0] = producerTrace
    }
    if (TEST_WRITER[3][0].operationName == "jms.produce") {
      def producerTrace = TEST_WRITER[3]
      TEST_WRITER[3] = TEST_WRITER[2]
      TEST_WRITER[2] = producerTrace
    }

    expect:
    receivedMessage.text == "responded!"
    assertTraces(4) {
      producerTrace(it, 0, jmsResourceName)
      consumerTrace(it, 1, jmsResourceName, false, ActiveMQMessageConsumer)
      producerTrace(it, 2, "Temporary Queue") // receive doesn't propagate the trace, so this is a root
      consumerTrace(it, 3, "Temporary Queue", false, ActiveMQMessageConsumer, TEST_WRITER[2][0])
    }

    where:
    destination                            | jmsResourceName
    session.createQueue("someSpringQueue") | "Queue someSpringQueue"
  }
}
