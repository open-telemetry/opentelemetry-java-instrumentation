import com.google.common.io.Files
import datadog.trace.agent.test.AgentTestRunner
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
import org.springframework.jms.core.JmsTemplate
import spock.lang.Shared

import javax.jms.Session
import javax.jms.TextMessage
import java.util.concurrent.TimeUnit

import static JMS2Test.consumerTrace
import static JMS2Test.producerTrace

class SpringTemplateJMS2Test extends AgentTestRunner {
  @Shared
  String messageText = "a message"
  @Shared
  JmsTemplate template
  @Shared
  Session session

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
    clientSession.createQueue("jms.queue.someSpringQueue", "jms.queue.someSpringQueue", true)
    clientSession.close()
    sf.close()
    serverLocator.close()

    def connectionFactory = HornetQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF,
      new TransportConfiguration(InVMConnectorFactory.name))

    def connection = connectionFactory.createConnection()
    connection.start()
    session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
    session.run()

    template = new JmsTemplate(connectionFactory)
    template.receiveTimeout = TimeUnit.SECONDS.toMillis(10)
  }

  def "sending a message to #jmsResourceName generates spans"() {
    setup:
    template.convertAndSend(destination, messageText)
    TextMessage receivedMessage = template.receive(destination)

    expect:
    receivedMessage.text == messageText
    assertTraces(2) {
      producerTrace(it, 0, jmsResourceName)
      consumerTrace(it, 1, jmsResourceName, false, HornetQMessageConsumer)
    }

    where:
    destination                            | jmsResourceName
    session.createQueue("someSpringQueue") | "Queue someSpringQueue"
  }

  def "send and receive message generates spans"() {
    setup:
    Thread.start {
      TEST_WRITER.waitForTraces(1)
      TextMessage msg = template.receive(destination)
      assert msg.text == messageText

      // There's a chance this might be reported last, messing up the assertion.
      template.send(msg.getJMSReplyTo()) {
        session -> template.getMessageConverter().toMessage("responded!", session)
      }
    }
    TextMessage receivedMessage = template.sendAndReceive(destination) {
      session -> template.getMessageConverter().toMessage(messageText, session)
    }

    TEST_WRITER.waitForTraces(4)
    // Manually reorder if reported in the wrong order.
    if (TEST_WRITER[3][0].operationName == "jms.produce") {
      def producerTrace = TEST_WRITER[3]
      TEST_WRITER[3] = TEST_WRITER[2]
      TEST_WRITER[2] = producerTrace
    }

    expect:
    receivedMessage.text == "responded!"
    assertTraces(4) {
      producerTrace(it, 0, jmsResourceName)
      consumerTrace(it, 1, jmsResourceName, false, HornetQMessageConsumer)
      producerTrace(it, 2, "Temporary Queue") // receive doesn't propagate the trace, so this is a root
      consumerTrace(it, 3, "Temporary Queue", false, HornetQMessageConsumer, TEST_WRITER[2][0])
    }

    where:
    destination                            | jmsResourceName
    session.createQueue("someSpringQueue") | "Queue someSpringQueue"
  }
}
