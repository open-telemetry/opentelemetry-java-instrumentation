/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.CONSUMER
import static io.opentelemetry.api.trace.SpanKind.PRODUCER

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification

class SpringIntegrationAndRabbitTest extends AgentInstrumentationSpecification implements WithRabbitProducerConsumerTrait {
  def setupSpec() {
    startRabbit()
  }

  def cleanupSpec() {
    stopRabbit()
  }

  def "should cooperate with existing RabbitMQ instrumentation"() {
    when:
    producerContext.getBean("producer", Runnable).run()

    then:
    assertTraces(2) {
      trace(0, 7) {
        span(0) {
          name "producer"
        }
        span(1) {
          name "testProducer.output"
          childOf span(0)
        }
        span(2) {
          name "exchange.declare"
          childOf span(1)
          kind CLIENT
        }
        span(3) {
          name "testTopic -> testTopic send"
          childOf span(1)
          kind PRODUCER
        }
        span(4) {
          name ~/testTopic.anonymous.[-\w]+ process/
          childOf span(3)
          kind CONSUMER
        }
        span(5) {
          name "testConsumer.input"
          childOf span(3)
        }
        span(6) {
          name "consumer"
          childOf span(5)
        }
      }

      trace(1, 1) {
        span(0) {
          name "basic.ack"
          kind CLIENT
        }
      }
    }
  }
}
