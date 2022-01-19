/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification

import java.util.stream.IntStream

class ForkJoinTaskTest extends AgentInstrumentationSpecification {

  def "test parallel"() {
    when:
    runWithSpan("parent") {
      IntStream.range(0, 20)
        .parallel()
        .forEach({ runWithSpan("child") {} })
    }

    then:
    assertTraces(1) {
      trace(0, 21) {
        span(0) {
          name "parent"
        }
        (1..20).each { index ->
          span(index) {
            childOf(span(0))
          }
        }
      }
    }
  }
}
