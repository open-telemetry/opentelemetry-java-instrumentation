/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static Jms2Test.consumerSpan
import static Jms2Test.producerSpan

import io.opentelemetry.instrumentation.test.AgentTestRunner
import javax.jms.ConnectionFactory
import listener.Config
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.jms.core.JmsTemplate

class SpringListenerJms2Test extends AgentTestRunner {
  def "receiving message in spring listener generates spans"() {
    setup:
    def context = new AnnotationConfigApplicationContext(Config)
    def factory = context.getBean(ConnectionFactory)
    def template = new JmsTemplate(factory)
    template.convertAndSend("SpringListenerJms2", "a message")

    expect:
    assertTraces(2) {
      trace(0, 2) {
        producerSpan(it, 0, "queue", "SpringListenerJms2")
        consumerSpan(it, 1, "queue", "SpringListenerJms2", "", span(0), "process")
      }
      trace(1, 1) {
        consumerSpan(it, 0, "queue", "SpringListenerJms2", "", null, "receive")
      }
    }

    cleanup:
    context.close()
  }
}
