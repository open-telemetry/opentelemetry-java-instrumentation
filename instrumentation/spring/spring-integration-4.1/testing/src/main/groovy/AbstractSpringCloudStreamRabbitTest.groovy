/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */


import io.opentelemetry.instrumentation.test.InstrumentationSpecification

import static io.opentelemetry.api.trace.SpanKind.CONSUMER

abstract class AbstractSpringCloudStreamRabbitTest extends InstrumentationSpecification implements WithRabbitProducerConsumerTrait {

  abstract Class<?> additionalContextClass()

  def setupSpec() {
    startRabbit(additionalContextClass())
  }

  def cleanupSpec() {
    stopRabbit()
  }

  def "should propagate context through RabbitMQ"() {
    when:
    producerContext.getBean("producer", Runnable).run()

    then:
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "producer"
        }
        span(1) {
          name "testConsumer.input process"
          childOf span(0)
          kind CONSUMER
        }
        span(2) {
          name "consumer"
          childOf span(1)
        }
      }
    }
  }
}
