/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static Jms1Test.consumerSpan
import static Jms1Test.producerSpan

import io.opentelemetry.instrumentation.test.AgentTestRunner
import javax.jms.ConnectionFactory
import listener.Config
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.jms.core.JmsTemplate

class SpringListenerJms1Test extends AgentTestRunner {

  def "receiving message in spring listener generates spans"() {
    setup:
    def context = new AnnotationConfigApplicationContext(Config)
    def factory = context.getBean(ConnectionFactory)
    def template = new JmsTemplate(factory)
    template.convertAndSend("SpringListenerJms1", "a message")

    expect:
    assertTraces(2) {
      trace(0, 2) {
        producerSpan(it, 0, "queue", "SpringListenerJms1")
        consumerSpan(it, 1, "queue", "SpringListenerJms1", "", span(0), "process")
      }
      trace(1, 1) {
        consumerSpan(it, 0, "queue", "SpringListenerJms1", "", null, "receive")
      }
    }

    cleanup:
    context.stop()
  }
}
