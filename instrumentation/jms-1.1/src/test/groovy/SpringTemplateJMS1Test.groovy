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

import com.google.common.base.Stopwatch
import io.opentelemetry.auto.test.AgentTestRunner
import org.apache.activemq.ActiveMQConnectionFactory
import org.apache.activemq.ActiveMQMessageConsumer
import org.apache.activemq.junit.EmbeddedActiveMQBroker
import org.springframework.jms.core.JmsTemplate
import spock.lang.Shared

import javax.jms.Connection
import javax.jms.Message
import javax.jms.Session
import javax.jms.TextMessage
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

import static JMS1Test.consumerSpan
import static JMS1Test.producerSpan

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
    ActiveMQConnectionFactory connectionFactory = broker.createConnectionFactory()
    Connection connection = connectionFactory.createConnection()
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

  def "sending a message to #expectedSpanName generates spans"() {
    setup:
    template.convertAndSend(destination, messageText)
    TextMessage receivedMessage = template.receive(destination)

    expect:
    receivedMessage.text == messageText
    assertTraces(2) {
      trace(0, 1) {
        producerSpan(it, 0, destinationType, destinationName)
      }
      trace(1, 1) {
        consumerSpan(it, 0, destinationType, destinationName, receivedMessage.getJMSMessageID(), false, ActiveMQMessageConsumer, traces[0][0])
      }
    }

    where:
    destination                               | destinationType | destinationName
    session.createQueue("SpringTemplateJMS1") | "queue"         | "SpringTemplateJMS1"
  }

  def "send and receive message generates spans"() {
    setup:
    AtomicReference<String> msgId = new AtomicReference<>()
    Thread.start {
      TextMessage msg = template.receive(destination)
      assert msg.text == messageText
      msgId.set(msg.getJMSMessageID())

      template.send(msg.getJMSReplyTo()) {
        session -> template.getMessageConverter().toMessage("responded!", session)
      }
    }
    def receivedMessage
    def stopwatch = Stopwatch.createStarted()
    while (receivedMessage == null && stopwatch.elapsed(TimeUnit.SECONDS) < 10) {
      // sendAndReceive() returns null if template.receive() has not been called yet
      receivedMessage = template.sendAndReceive(destination) {
        session -> template.getMessageConverter().toMessage(messageText, session)
      }
    }

    expect:
    receivedMessage.text == "responded!"
    assertTraces(4) {
      trace(0, 1) {
        producerSpan(it, 0, destinationType, destinationName)
      }
      trace(1, 1) {
        consumerSpan(it, 0, destinationType, destinationName, msgId.get(), false, ActiveMQMessageConsumer, traces[0][0])
      }
      trace(2, 1) {
        producerSpan(it, 0, "queue", "<temporary>") // receive doesn't propagate the trace, so this is a root
      }
      trace(3, 1) {
        consumerSpan(it, 0, "queue", "<temporary>", receivedMessage.getJMSMessageID(), false, ActiveMQMessageConsumer, traces[2][0])
      }
    }

    where:
    destination                               | destinationType | destinationName
    session.createQueue("SpringTemplateJMS1") | "queue"         | "SpringTemplateJMS1"
  }
}
