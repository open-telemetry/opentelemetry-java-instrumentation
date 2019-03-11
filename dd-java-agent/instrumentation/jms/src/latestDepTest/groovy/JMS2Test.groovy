import com.google.common.io.Files
import datadog.opentracing.DDSpan
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.asserts.ListWriterAssert
import datadog.trace.api.DDSpanTypes
import io.opentracing.tag.Tags
import org.hornetq.api.core.TransportConfiguration
import org.hornetq.api.core.client.HornetQClient
import org.hornetq.api.jms.HornetQJMSClient
import org.hornetq.api.jms.JMSFactoryType
import org.hornetq.core.config.Configuration
import org.hornetq.core.config.CoreQueueConfiguration
import org.hornetq.core.config.impl.ConfigurationImpl
import org.hornetq.core.remoting.impl.invm.InVMAcceptorFactory
import org.hornetq.core.remoting.impl.invm.InVMConnectorFactory
import org.hornetq.core.remoting.impl.netty.NettyAcceptorFactory
import org.hornetq.core.server.HornetQServer
import org.hornetq.core.server.HornetQServers
import org.hornetq.jms.client.HornetQMessageConsumer
import org.hornetq.jms.client.HornetQMessageProducer
import org.hornetq.jms.client.HornetQTextMessage
import spock.lang.Shared

import javax.jms.Message
import javax.jms.MessageListener
import javax.jms.Session
import javax.jms.TextMessage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

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
    config.setAcceptorConfigurations([new TransportConfiguration(NettyAcceptorFactory.name),
                                      new TransportConfiguration(InVMAcceptorFactory.name)].toSet())

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

  def "sending a message to #jmsResourceName generates spans"() {
    setup:
    def producer = session.createProducer(destination)
    def consumer = session.createConsumer(destination)

    producer.send(message)

    TextMessage receivedMessage = consumer.receive()

    expect:
    receivedMessage.text == messageText
    assertTraces(2) {
      producerTrace(it, 0, jmsResourceName)
      consumerTrace(it, 1, jmsResourceName, false, HornetQMessageConsumer)
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
    assertTraces(2) {
      producerTrace(it, 0, jmsResourceName)
      consumerTrace(it, 1, jmsResourceName, true, consumer.messageListener.class)
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
          serviceName "jms"
          operationName "jms.consume"
          resourceName "JMS receiveNoWait"
          spanType DDSpanTypes.MESSAGE_PRODUCER
          errored false

          tags {
            defaultTags()
            "${Tags.COMPONENT.key}" "jms"
            "${Tags.SPAN_KIND.key}" "consumer"
            "span.origin.type" HornetQMessageConsumer.name
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
          serviceName "jms"
          operationName "jms.consume"
          resourceName "JMS receive"
          spanType DDSpanTypes.MESSAGE_PRODUCER
          errored false

          tags {
            defaultTags()
            "${Tags.COMPONENT.key}" "jms"
            "${Tags.SPAN_KIND.key}" "consumer"
            "span.origin.type" HornetQMessageConsumer.name
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

  static producerTrace(ListWriterAssert writer, int index, String jmsResourceName) {
    writer.trace(index, 1) {
      span(0) {
        parent()
        serviceName "jms"
        operationName "jms.produce"
        resourceName "Produced for $jmsResourceName"
        spanType DDSpanTypes.MESSAGE_PRODUCER
        errored false

        tags {
          defaultTags()
          "${Tags.COMPONENT.key}" "jms"
          "${Tags.SPAN_KIND.key}" "producer"
          "span.origin.type" HornetQMessageProducer.name
        }
      }
    }
  }

  static consumerTrace(ListWriterAssert writer, int index, String jmsResourceName, boolean messageListener, Class origin, DDSpan parentSpan = TEST_WRITER[0][0]) {
    writer.trace(index, 1) {
      span(0) {
        childOf parentSpan
        serviceName "jms"
        if (messageListener) {
          operationName "jms.onMessage"
          resourceName "Received from $jmsResourceName"
        } else {
          operationName "jms.consume"
          resourceName "Consumed from $jmsResourceName"
        }
        spanType DDSpanTypes.MESSAGE_CONSUMER
        errored false

        tags {
          defaultTags(true)
          "${Tags.COMPONENT.key}" "jms"
          "${Tags.SPAN_KIND.key}" "consumer"
          "span.origin.type" origin.name
        }
      }
    }
  }
}
