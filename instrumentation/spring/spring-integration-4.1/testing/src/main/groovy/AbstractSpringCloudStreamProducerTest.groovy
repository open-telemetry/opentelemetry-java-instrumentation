/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.InstrumentationSpecification

import static io.opentelemetry.api.trace.SpanKind.CONSUMER
import static io.opentelemetry.api.trace.SpanKind.PRODUCER
import static org.junit.jupiter.api.Assumptions.assumeTrue

abstract class AbstractSpringCloudStreamProducerTest extends InstrumentationSpecification implements WithRabbitProducerConsumerTrait {
  private static final boolean HAS_PRODUCER_SPAN = Boolean.getBoolean("otel.instrumentation.spring-integration.producer.enabled")

  abstract Class<?> additionalContextClass()

  def setupSpec() {
    startRabbit(additionalContextClass())
  }

  def cleanupSpec() {
    stopRabbit()
  }

  def "has producer span"() {
    assumeTrue(HAS_PRODUCER_SPAN)

    when:
    producerContext.getBean("producer", Runnable).run()

    then:
    assertTraces(1) {
      trace(0, 4) {
        span(0) {
          name "producer"
        }
        span(1) {
          name "testProducer.output send"
          childOf span(0)
          kind PRODUCER
        }
        span(2) {
          name "testConsumer.input process"
          childOf span(1)
          kind CONSUMER
        }
        span(3) {
          name "consumer"
          childOf span(2)
        }
      }
    }
  }
}
