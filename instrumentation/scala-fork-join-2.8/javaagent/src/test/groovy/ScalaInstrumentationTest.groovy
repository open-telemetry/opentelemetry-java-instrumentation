/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification

class ScalaInstrumentationTest extends AgentInstrumentationSpecification {

  def "scala futures and callbacks"() {
    setup:
    ScalaConcurrentTests scalaTest = new ScalaConcurrentTests()

    when:
    scalaTest.traceWithFutureAndCallbacks()

    then:
    assertTraces(1) {
      trace(0, 5) {
        span(0) {
          name "parent"
          attributes {
          }
        }
        span("goodFuture") {
          childOf span(0)
          attributes {
          }
        }
        span("badFuture") {
          childOf span(0)
          attributes {
          }
        }
        span("successCallback") {
          childOf span(0)
          attributes {
          }
        }
        span("failureCallback") {
          childOf span(0)
          attributes {
          }
        }
      }
    }
  }

  def "scala propagates across futures with no traces"() {
    setup:
    ScalaConcurrentTests scalaTest = new ScalaConcurrentTests()

    when:
    scalaTest.tracedAcrossThreadsWithNoTrace()

    then:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "parent"
          attributes {
          }
        }
        span("callback") {
          childOf span(0)
          attributes {
          }
        }
      }
    }
  }

  def "scala either promise completion"() {
    setup:
    ScalaConcurrentTests scalaTest = new ScalaConcurrentTests()

    when:
    scalaTest.traceWithPromises()

    then:
    assertTraces(1) {
      trace(0, 5) {
        span(0) {
          name "parent"
          attributes {
          }
        }
        span("future1") {
          childOf span(0)
          attributes {
          }
        }
        span("keptPromise") {
          childOf span(0)
          attributes {
          }
        }
        span("keptPromise2") {
          childOf span(0)
          attributes {
          }
        }
        span("brokenPromise") {
          childOf span(0)
          attributes {
          }
        }
      }
    }
  }

  def "scala first completed future"() {
    setup:
    ScalaConcurrentTests scalaTest = new ScalaConcurrentTests()

    when:
    scalaTest.tracedWithFutureFirstCompletions()

    then:
    assertTraces(1) {
      trace(0, 4) {
        span(0) {
          name "parent"
          attributes {
          }
        }
        span("timeout1") {
          childOf span(0)
          attributes {
          }
        }
        span("timeout2") {
          childOf span(0)
          attributes {
          }
        }
        span("timeout3") {
          childOf span(0)
          attributes {
          }
        }
      }
    }
  }
}
