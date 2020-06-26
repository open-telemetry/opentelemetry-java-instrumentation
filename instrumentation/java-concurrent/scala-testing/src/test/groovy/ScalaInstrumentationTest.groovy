/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import io.opentelemetry.auto.test.AgentTestRunner

class ScalaInstrumentationTest extends AgentTestRunner {

  def "scala futures and callbacks"() {
    setup:
    ScalaConcurrentTests scalaTest = new ScalaConcurrentTests()

    when:
    scalaTest.traceWithFutureAndCallbacks()

    then:
    assertTraces(1) {
      trace(0, 5) {
        span(0) {
          operationName "parent"
          tags {
          }
        }
        span("goodFuture") {
          childOf span(0)
          tags {
          }
        }
        span("badFuture") {
          childOf span(0)
          tags {
          }
        }
        span("successCallback") {
          childOf span(0)
          tags {
          }
        }
        span("failureCallback") {
          childOf span(0)
          tags {
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
          operationName "parent"
          tags {
          }
        }
        span("callback") {
          childOf span(0)
          tags {
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
          operationName "parent"
          tags {
          }
        }
        span("future1") {
          childOf span(0)
          tags {
          }
        }
        span("keptPromise") {
          childOf span(0)
          tags {
          }
        }
        span("keptPromise2") {
          childOf span(0)
          tags {
          }
        }
        span("brokenPromise") {
          childOf span(0)
          tags {
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
          operationName "parent"
          tags {
          }
        }
        span("timeout1") {
          childOf span(0)
          tags {
          }
        }
        span("timeout2") {
          childOf span(0)
          tags {
          }
        }
        span("timeout3") {
          childOf span(0)
          tags {
          }
        }
      }
    }
  }
}
