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

import static JMS1Test.consumerSpan
import static JMS1Test.producerSpan

import com.google.common.base.Stopwatch
import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.instrumentation.auto.jms.JMSTracer
import io.opentelemetry.instrumentation.auto.jms.Operation
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.jms.Connection
import javax.jms.Session
import javax.jms.TextMessage
import org.apache.activemq.ActiveMQConnectionFactory
import org.apache.activemq.junit.EmbeddedActiveMQBroker
import org.springframework.jms.core.JmsTemplate
import spock.lang.Shared

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

  def "sending a message to #destinationName generates spans"() {
    setup:
    template.convertAndSend(destination, messageText)
    TextMessage receivedMessage = template.receive(destination)

    expect:
    receivedMessage.text == messageText
    assertTraces(1) {
      trace(0, 2) {
        producerSpan(it, 0, destinationType, destinationName)
        consumerSpan(it, 1, destinationType, destinationName, receivedMessage.getJMSMessageID(), span(0), Operation.receive)
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
    assertTraces(2) {
      trace(0, 2) {
        producerSpan(it, 0, destinationType, destinationName)
        consumerSpan(it, 1, destinationType, destinationName, msgId.get(), span(0), Operation.receive)
      }
      trace(1, 2) {
        // receive doesn't propagate the trace, so this is a root
        producerSpan(it, 0, "queue", JMSTracer.TEMP_DESTINATION_NAME)
        consumerSpan(it, 1, "queue", JMSTracer.TEMP_DESTINATION_NAME, receivedMessage.getJMSMessageID(), span(0), Operation.receive)
      }
    }

    where:
    destination                               | destinationType | destinationName
    session.createQueue("SpringTemplateJMS1") | "queue"         | "SpringTemplateJMS1"
  }
}
