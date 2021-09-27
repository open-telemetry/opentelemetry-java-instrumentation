/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */


import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import listener.Config
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.jms.core.JmsTemplate

import javax.jms.ConnectionFactory

class SpringListenerJms2SuppressReceiveSpansTest extends AgentInstrumentationSpecification {
  def "receiving message in spring listener generates spans"() {
    setup:
    def context = new AnnotationConfigApplicationContext(Config)
    def factory = context.getBean(ConnectionFactory)
    def template = new JmsTemplate(factory)

    template.convertAndSend("SpringListenerJms2", "a message")

    expect:
    assertTraces(1) {
      trace(0, 2) {
        Jms2Test.producerSpan(it, 0, "queue", "SpringListenerJms2")
        Jms2Test.consumerSpan(it, 1, "queue", "SpringListenerJms2", "", span(0), "process")
      }
    }

    cleanup:
    context.close()
  }
}
