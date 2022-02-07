/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ThreadPoolDispatcherKt

class KotlinCoroutineInstrumentationTest extends AgentInstrumentationSpecification {

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
          name "parent"
          attributes {
          }
        }
        (0..2).each {
          span("produce_$it") {
            childOf span(0)
            attributes {
            }
          }
          span("consume_$it") {
            childOf span(0)
            attributes {
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
          name "parent"
          attributes {
          }
        }
        span("preLaunch") {
          childOf span(0)
          attributes {
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
          name "parent"
          attributes {
          }
        }
        span("nested") {
          childOf span(0)
          attributes {
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

    where:
    dispatcher << dispatchersToTest
  }

  def "test concurrent suspend functions"() {
    setup:
    KotlinCoroutineTests kotlinTest = new KotlinCoroutineTests(Dispatchers.Default)
    int numIters = 100
    HashSet<Long> seenItersA = new HashSet<>()
    HashSet<Long> seenItersB = new HashSet<>()
    HashSet<Long> expectedIters = new HashSet<>((0L..(numIters - 1)).toList())

    when:
    kotlinTest.launchConcurrentSuspendFunctions(numIters)

    then:
    // This generates numIters each of "a calls a2" and "b calls b2" traces.  Each
    // trace should have a single pair of spans (a and a2) and each of those spans
    // should have the same iteration number (attribute "iter").
    // The traces are in some random order, so let's keep track and make sure we see
    // each iteration # exactly once
    assertTraces(numIters * 2) {
      for (int i = 0; i < numIters * 2; i++) {
        trace(i, 2) {
          boolean a = false
          long iter = -1
          span(0) {
            a = span.name.matches("a")
            iter = span.getAttributes().get(AttributeKey.longKey("iter"))
            (a ? seenItersA : seenItersB).add(iter)
            name(a ? "a" : "b")
          }
          span(1) {
            name(a ? "a2" : "b2")
            childOf(span(0))
            assert span.getAttributes().get(AttributeKey.longKey("iter")) == iter

          }
        }
      }
    }
    assert seenItersA.equals(expectedIters)
    assert seenItersB.equals(expectedIters)
  }

  def "kotlin traced mono"() {
    setup:
    KotlinCoroutineTests kotlinTest = new KotlinCoroutineTests(dispatcher)

    when:
    kotlinTest.tracedMono()

    then:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "parent"
          attributes {
          }
        }
        span("child") {
          childOf span(0)
          attributes {
          }
        }
      }
    }

    where:
    dispatcher << dispatchersToTest
  }

  def "kotlin traced mono with context propagation operator"() {
    setup:
    KotlinCoroutineTests kotlinTest = new KotlinCoroutineTests(dispatcher)

    when:
    kotlinTest.tracedMonoContextPropagationOperator()

    then:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "parent"
          attributes {
          }
        }
        span("child") {
          childOf span(0)
          attributes {
          }
        }
      }
    }

    where:
    dispatcher << dispatchersToTest
  }

  def "kotlin traced flux"() {
    setup:
    KotlinCoroutineTests kotlinTest = new KotlinCoroutineTests(dispatcher)

    when:
    kotlinTest.tracedFlux()

    then:
    assertTraces(1) {
      trace(0, 4) {
        span(0) {
          name "parent"
          attributes {
          }
        }
        (0..2).each {
          span("child_$it") {
            childOf span(0)
            attributes {
            }
          }
        }
      }
    }

    where:
    dispatcher << dispatchersToTest
  }
}
