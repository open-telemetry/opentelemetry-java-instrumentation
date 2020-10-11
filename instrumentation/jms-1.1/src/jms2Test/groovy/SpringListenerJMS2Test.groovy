/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static JMS2Test.consumerSpan
import static JMS2Test.producerSpan

import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.instrumentation.auto.jms.Operation
import javax.jms.ConnectionFactory
import listener.Config
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.jms.core.JmsTemplate

class SpringListenerJMS2Test extends AgentTestRunner {
  def "receiving message in spring listener generates spans"() {
    setup:
    def context = new AnnotationConfigApplicationContext(Config)
    def factory = context.getBean(ConnectionFactory)
    def template = new JmsTemplate(factory)
    template.convertAndSend("SpringListenerJMS2", "a message")

    expect:
    assertTraces(2) {
      trace(0, 2) {
        producerSpan(it, 0, "queue", "SpringListenerJMS2")
        consumerSpan(it, 1, "queue", "SpringListenerJMS2", "", span(0), Operation.process)
      }
      trace(1, 1) {
        consumerSpan(it, 0, "queue", "SpringListenerJMS2", "", null, Operation.receive)
      }
    }

    cleanup:
    context.close()
  }
}
