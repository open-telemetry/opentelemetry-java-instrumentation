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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ThreadPoolDispatcherKt

class KotlinCoroutineInstrumentationTest extends AgentTestRunner {

  static dispatchersToTest = [
    Dispatchers.Default,
    Dispatchers.IO,
    Dispatchers.Unconfined,
    ThreadPoolDispatcherKt.newFixedThreadPoolContext(2, "Fixed-Thread-Pool"),
    ThreadPoolDispatcherKt.newSingleThreadContext("Single-Thread"),
  ]

  def "kotlin traced across channels"() {
    setup:
    KotlinCoroutineTests kotlinTest = new KotlinCoroutineTests(dispatcher)

    when:
    kotlinTest.tracedAcrossChannels()

    then:
    assertTraces(1) {
      trace(0, 7) {
        span(0) {
          operationName "parent"
          tags {
          }
        }
        (0..2).each {
          span("produce_$it") {
            childOf span(0)
            tags {
            }
          }
          span("consume_$it") {
            childOf span(0)
            tags {
            }
          }
        }
      }
    }

    where:
    dispatcher << dispatchersToTest
  }

  def "kotlin cancellation prevents trace"() {
    setup:
    KotlinCoroutineTests kotlinTest = new KotlinCoroutineTests(dispatcher)

    when:
    kotlinTest.tracePreventedByCancellation()

    then:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName "parent"
          tags {
          }
        }
        span("preLaunch") {
          childOf span(0)
          tags {
          }
        }
      }
    }

    where:
    dispatcher << dispatchersToTest
  }

  def "kotlin propagates across nested jobs"() {
    setup:
    KotlinCoroutineTests kotlinTest = new KotlinCoroutineTests(dispatcher)

    when:
    kotlinTest.tracedAcrossThreadsWithNested()

    then:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName "parent"
          tags {
          }
        }
        span("nested") {
          childOf span(0)
          tags {
          }
        }
      }
    }

    where:
    dispatcher << dispatchersToTest
  }

  def "kotlin either deferred completion"() {
    setup:
    KotlinCoroutineTests kotlinTest = new KotlinCoroutineTests(Dispatchers.Default)

    when:
    kotlinTest.traceWithDeferred()

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

    where:
    dispatcher << dispatchersToTest
  }

  def "kotlin first completed deferred"() {
    setup:
    KotlinCoroutineTests kotlinTest = new KotlinCoroutineTests(Dispatchers.Default)

    when:
    kotlinTest.tracedWithDeferredFirstCompletions()

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

    where:
    dispatcher << dispatchersToTest
  }
}
