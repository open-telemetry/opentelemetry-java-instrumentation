/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.test.base


import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification

// TODO: add a test for a longer chain of promises
abstract class AbstractPromiseTest<P, M> extends AgentInstrumentationSpecification {

  abstract P newPromise()

  abstract M map(P promise, Closure<String> callback)

  abstract void onComplete(M promise, Closure callback)

  abstract void complete(P promise, boolean value)

  abstract Boolean get(P promise)

  def "test call with parent"() {
    setup:
    def promise = newPromise()

    when:
    runWithSpan("parent") {
      def mapped = map(promise) { "$it" }
      onComplete(mapped) {
        assert it == "$value"
        runWithSpan("callback") {}
      }
      runWithSpan("other") {
        complete(promise, value)
      }
    }

    then:
    get(promise) == value
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        span(1) {
          name "other"
          kind SpanKind.INTERNAL
          childOf span(0)
        }
        span(2) {
          name "callback"
          kind SpanKind.INTERNAL
          childOf span(0)
        }
      }
    }

    where:
    value << [true, false]
  }

  def "test call with parent delayed complete"() {
    setup:
    def promise = newPromise()

    when:
    runWithSpan("parent") {
      def mapped = map(promise) { "$it" }
      onComplete(mapped) {
        assert it == "$value"
        runWithSpan("callback") {}
      }
    }

    runWithSpan("other") {
      complete(promise, value)
    }

    then:
    get(promise) == value
    assertTraces(2) {
      trace(0, 2) {
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        span(1) {
          name "callback"
          kind SpanKind.INTERNAL
          childOf span(0)
        }
      }
      trace(1, 1) {
        span(0) {
          name "other"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
      }
    }

    where:
    value << [true, false]
  }

  def "test call with parent complete separate thread"() {
    setup:
    final promise = newPromise()

    when:
    runWithSpan("parent") {
      def mapped = map(promise) { "$it" }
      onComplete(mapped) {
        assert it == "$value"
        runWithSpan("callback") {}
      }
      Thread.start {
        complete(promise, value)
      }.join()
    }

    then:
    get(promise) == value
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        span(1) {
          name "callback"
          kind SpanKind.INTERNAL
          childOf span(0)
        }
      }
    }

    where:
    value << [true, false]
  }

  def "test call with no parent"() {
    setup:
    def promise = newPromise()

    when:
    def mapped = map(promise) { "$it" }
    onComplete(mapped) {
      assert it == "$value"
      runWithSpan("callback") {}
    }

    runWithSpan("other") {
      complete(promise, value)
    }

    then:
    get(promise) == value
    assertTraces(1) {
      trace(0, 2) {
        // TODO: is this really the behavior we want?
        span(0) {
          name "other"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        span(1) {
          name "callback"
          kind SpanKind.INTERNAL
          childOf span(0)
        }
      }
    }

    where:
    value << [true, false]
  }
}
