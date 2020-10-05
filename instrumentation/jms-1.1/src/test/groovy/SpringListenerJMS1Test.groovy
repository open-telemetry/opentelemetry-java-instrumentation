/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static JMS1Test.consumerSpan
import static JMS1Test.producerSpan

import io.opentelemetry.auto.test.AgentTestRunner
import javax.jms.ConnectionFactory
import listener.Config
import org.apache.activemq.ActiveMQMessageConsumer
import org.apache.activemq.junit.EmbeddedActiveMQBroker
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.jms.core.JmsTemplate
import org.springframework.jms.listener.adapter.MessagingMessageListenerAdapter

class SpringListenerJMS1Test extends AgentTestRunner {

  def "receiving message in spring listener generates spans"() {
    setup:
    def context = new AnnotationConfigApplicationContext(Config)
    def factory = context.getBean(ConnectionFactory)
    def template = new JmsTemplate(factory)
    template.convertAndSend("SpringListenerJMS1", "a message")

    expect:
    assertTraces(2) {
      trace(0, 2) {
        producerSpan(it, 0, "queue", "SpringListenerJMS1")
        consumerSpan(it, 1, "queue", "SpringListenerJMS1", null, true, MessagingMessageListenerAdapter, span(0))
      }
      trace(1, 1) {
        consumerSpan(it, 0, "queue", "SpringListenerJMS1", null, false, ActiveMQMessageConsumer, traces[0][0])
      }
    }

    cleanup:
    context.getBean(EmbeddedActiveMQBroker).stop()
  }
}
