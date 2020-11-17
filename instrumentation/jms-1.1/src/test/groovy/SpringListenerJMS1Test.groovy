/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static JMS1Test.consumerSpan
import static JMS1Test.producerSpan

import io.opentelemetry.instrumentation.test.AgentTestRunner
import javax.jms.ConnectionFactory
import listener.Config
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.jms.core.JmsTemplate

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
        consumerSpan(it, 1, "queue", "SpringListenerJMS1", "", span(0), "process")
      }
      trace(1, 1) {
        consumerSpan(it, 0, "queue", "SpringListenerJMS1", "", null, "receive")
      }
    }

    cleanup:
    context.stop()
  }
}
