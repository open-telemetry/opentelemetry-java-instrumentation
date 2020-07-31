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

import com.google.common.io.Files
import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.auto.test.utils.ConfigUtils
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
import org.springframework.jms.core.JmsTemplate
import spock.lang.Shared

import javax.jms.Session
import javax.jms.TextMessage
import java.util.concurrent.TimeUnit

import static JMS2Test.consumerSpan
import static JMS2Test.producerSpan

class SpringTemplateJMS2Test extends AgentTestRunner {
  static {
    ConfigUtils.updateConfig {
      System.setProperty("otel.trace.classes.exclude", "org.springframework.jms.config.JmsListenerEndpointRegistry\$AggregatingCallback,org.springframework.context.support.DefaultLifecycleProcessor\$1")
    }
  }

  @Shared
  HornetQServer server
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

    server = HornetQServers.newHornetQServer(config)
    server.start()

    def serverLocator = HornetQClient.createServerLocatorWithoutHA(new TransportConfiguration(InVMConnectorFactory.name))
    def sf = serverLocator.createSessionFactory()
    def clientSession = sf.createSession(false, false, false)
    clientSession.createQueue("jms.queue.SpringTemplateJMS2", "jms.queue.SpringTemplateJMS2", true)
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

  def cleanupSpec() {
    server.stop()
    ConfigUtils.updateConfig {
      System.clearProperty("otel.trace.classes.exclude")
    }
  }

  def "sending a message to #expectedSpanName generates spans"() {
    setup:
    template.convertAndSend(destination, messageText)
    TextMessage receivedMessage = template.receive(destination)

    expect:
    receivedMessage.text == messageText
    assertTraces(2) {
      trace(0, 1) {
        producerSpan(it, 0, expectedSpanName)
      }
      trace(1, 1) {
        consumerSpan(it, 0, expectedSpanName, false, HornetQMessageConsumer, traces[0][0])
      }
    }

    where:
    destination                               | expectedSpanName
    session.createQueue("SpringTemplateJMS2") | "queue/SpringTemplateJMS2"
  }

  def "send and receive message generates spans"() {
    setup:
    Thread.start {
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

    expect:
    receivedMessage.text == "responded!"
    assertTraces(4) {
      trace(0, 1) {
        producerSpan(it, 0, expectedSpanName)
      }
      trace(1, 1) {
        consumerSpan(it, 0, expectedSpanName, false, HornetQMessageConsumer, traces[0][0])
      }
      trace(2, 1) {
        producerSpan(it, 0, "queue/<temporary>") // receive doesn't propagate the trace, so this is a root
      }
      trace(3, 1) {
        consumerSpan(it, 0, "queue/<temporary>", false, HornetQMessageConsumer, traces[2][0])
      }
    }

    where:
    destination                               | expectedSpanName
    session.createQueue("SpringTemplateJMS2") | "queue/SpringTemplateJMS2"
  }
}
