import com.google.common.io.Files
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.DDSpanTypes
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
import org.hornetq.core.server.HornetQServers
import org.hornetq.jms.client.HornetQMessageConsumer
import org.hornetq.jms.client.HornetQMessageProducer
import spock.lang.Shared
import spock.lang.Unroll

import javax.jms.Message
import javax.jms.MessageListener
import javax.jms.Session
import javax.jms.TextMessage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

class JMS2Test extends AgentTestRunner {
  @Shared
  static Session session

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

    HornetQServers.newHornetQServer(config).start()

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
    trace.size() == 1

    def producerSpan = trace[0]

    producerSpan.context().operationName == "jms.produce"
    producerSpan.serviceName == "jms"
    producerSpan.resourceName == "Produced for $resourceName"
    producerSpan.type == DDSpanTypes.MESSAGE_PRODUCER
    !producerSpan.context().getErrorFlag()
    producerSpan.context().parentId == 0


    def producerTags = producerSpan.context().tags
    producerTags["span.kind"] == "producer"
    producerTags["component"] == "jms2"

    producerTags["span.origin.type"] == HornetQMessageProducer.name

    producerTags["thread.name"] != null
    producerTags["thread.id"] != null
    producerTags.size() == 6

    and: // consumer trace
    def consumerTrace = TEST_WRITER.get(1)
    consumerTrace.size() == 1

    def consumerSpan = consumerTrace[0]

    consumerSpan.context().operationName == "jms.consume"
    consumerSpan.serviceName == "jms"
    consumerSpan.resourceName == "Consumed from $resourceName"
    consumerSpan.type == DDSpanTypes.MESSAGE_CONSUMER
    !consumerSpan.context().getErrorFlag()
    consumerSpan.context().parentId == producerSpan.context().spanId


    def consumerTags = consumerSpan.context().tags
    consumerTags["span.kind"] == "consumer"
    consumerTags["component"] == "jms2"

    consumerTags["span.origin.type"] == HornetQMessageConsumer.name

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
    trace.size() == 1

    def producerSpan = trace[0]

    producerSpan.context().operationName == "jms.produce"
    producerSpan.serviceName == "jms"
    producerSpan.resourceName == "Produced for $resourceName"
    producerSpan.type == DDSpanTypes.MESSAGE_PRODUCER
    !producerSpan.context().getErrorFlag()
    producerSpan.context().parentId == 0


    def producerTags = producerSpan.context().tags
    producerTags["span.kind"] == "producer"
    producerTags["component"] == "jms2"

    producerTags["span.origin.type"] == HornetQMessageProducer.name

    producerTags["thread.name"] != null
    producerTags["thread.id"] != null
    producerTags.size() == 6

    and: // consumer trace
    def consumerTrace = TEST_WRITER.get(1)
    consumerTrace.size() == 1

    def consumerSpan = consumerTrace[0]

    consumerSpan.context().operationName == "jms.onMessage"
    consumerSpan.serviceName == "jms"
    consumerSpan.resourceName == "Received from $resourceName"
    consumerSpan.type == DDSpanTypes.MESSAGE_CONSUMER
    !consumerSpan.context().getErrorFlag()
    consumerSpan.context().parentId == producerSpan.context().spanId


    def consumerTags = consumerSpan.context().tags
    consumerTags["span.kind"] == "consumer"
    consumerTags["component"] == "jms2"

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
