/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static Jms1Test.consumerSpan
import static Jms1Test.producerSpan
import static io.opentelemetry.api.trace.SpanKind.PRODUCER

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import javax.jms.ConnectionFactory
import listener.Config
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.jms.core.JmsTemplate

class SpringListenerJms1Test extends AgentInstrumentationSpecification {

  def "receiving message in spring listener generates spans"() {
    setup:
    def context = new AnnotationConfigApplicationContext(Config)
    def factory = context.getBean(ConnectionFactory)
    def template = new JmsTemplate(factory)

    template.convertAndSend("SpringListenerJms1", "a message")

    expect:
    assertTraces(2) {
      sortTraces {
        // ensure that traces appear in expected order
        if (traces[0][0].kind == PRODUCER) {
          def tmp = traces[0]
          traces[0] = traces[1]
          traces[1] = tmp
        }
      }

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
