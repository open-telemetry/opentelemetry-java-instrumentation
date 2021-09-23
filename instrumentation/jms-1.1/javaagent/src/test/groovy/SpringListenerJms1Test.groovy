/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import listener.Config
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.jms.core.JmsTemplate

import javax.jms.ConnectionFactory

import static Jms1Test.consumerSpan
import static Jms1Test.producerSpan
import static io.opentelemetry.api.trace.SpanKind.CONSUMER
import static io.opentelemetry.api.trace.SpanKind.PRODUCER

class SpringListenerJms1Test extends AgentInstrumentationSpecification {

  def "receiving message in spring listener generates spans"() {
    setup:
    def context = new AnnotationConfigApplicationContext(Config)
    def factory = context.getBean(ConnectionFactory)
    def template = new JmsTemplate(factory)

    template.convertAndSend("SpringListenerJms1", "a message")

    expect:
    assertTraces(2) {
      traces.sort(orderByRootSpanKind(CONSUMER, PRODUCER))

      trace(0, 1) {
        consumerSpan(it, 0, "queue", "SpringListenerJms1", "", null, "receive")
      }
      trace(1, 2) {
        producerSpan(it, 0, "queue", "SpringListenerJms1")
        consumerSpan(it, 1, "queue", "SpringListenerJms1", "", span(0), "process")
      }
    }

    cleanup:
    context.stop()
  }
}
