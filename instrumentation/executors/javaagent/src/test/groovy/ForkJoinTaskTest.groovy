/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification

import java.util.stream.IntStream

import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderTrace

class ForkJoinTaskTest extends AgentInstrumentationSpecification {

  def "test parallel"() {
    when:
    runUnderTrace("parent") {
      IntStream.range(0, 20)
        .parallel()
        .forEach({ runUnderTrace("child") {} })
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
