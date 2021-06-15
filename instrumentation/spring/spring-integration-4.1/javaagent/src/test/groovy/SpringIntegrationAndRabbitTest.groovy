/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.CONSUMER
import static io.opentelemetry.api.trace.SpanKind.PRODUCER
import static io.opentelemetry.api.trace.SpanKind.SERVER
import static io.opentelemetry.instrumentation.test.server.ServerTraceUtils.runUnderServerTrace

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
    // simulate the workflow being triggered by HTTP request
    runUnderServerTrace("HTTP GET") {
      producerContext.getBean("producer", Runnable).run()
    }

    then:
    assertTraces(2) {
      trace(0, 7) {
        span(0) {
          name "HTTP GET"
          kind SERVER
        }
        span(1) {
          name "producer"
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
        // spring-cloud-stream-binder-rabbit listener puts all messages into a BlockingQueue immediately after receiving
        // that's why the rabbitmq CONSUMER span will never have any child span (and propagate context, actually)
        // and that's why spring-integration creates another CONSUMER span
        span(4) {
          name ~/testTopic.anonymous.[-\w]+ process/
          childOf span(3)
          kind CONSUMER
        }
        span(5) {
          name "testConsumer.input"
          childOf span(3)
          kind CONSUMER
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
