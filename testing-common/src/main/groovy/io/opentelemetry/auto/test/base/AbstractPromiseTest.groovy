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

package io.opentelemetry.auto.test.base

import static io.opentelemetry.auto.test.utils.TraceUtils.basicSpan
import static io.opentelemetry.auto.test.utils.TraceUtils.runUnderTrace

import io.opentelemetry.auto.test.AgentTestRunner

// TODO: add a test for a longer chain of promises
abstract class AbstractPromiseTest<P, M> extends AgentTestRunner {

  abstract P newPromise()

  abstract M map(P promise, Closure<String> callback)

  abstract void onComplete(M promise, Closure callback)

  abstract void complete(P promise, boolean value)

  abstract Boolean get(P promise)

  def "test call with parent"() {
    setup:
    def promise = newPromise()

    when:
    runUnderTrace("parent") {
      def mapped = map(promise) { "$it" }
      onComplete(mapped) {
        assert it == "$value"
        runUnderTrace("callback") {}
      }
      runUnderTrace("other") {
        complete(promise, value)
      }
    }

    then:
    get(promise) == value
    assertTraces(1) {
      trace(0, 3) {
        basicSpan(it, 0, "parent")
        basicSpan(it, 1, "other", it.span(0))
        basicSpan(it, 2, "callback", it.span(0))
      }
    }

    where:
    value << [true, false]
  }

  def "test call with parent delayed complete"() {
    setup:
    def promise = newPromise()

    when:
    runUnderTrace("parent") {
      def mapped = map(promise) { "$it" }
      onComplete(mapped) {
        assert it == "$value"
        runUnderTrace("callback") {}
      }
    }

    runUnderTrace("other") {
      complete(promise, value)
    }

    then:
    get(promise) == value
    assertTraces(2) {
      trace(0, 2) {
        basicSpan(it, 0, "parent")
        basicSpan(it, 1, "callback", span(0))
      }
      trace(1, 1) {
        basicSpan(it, 0, "other")
      }
    }

    where:
    value << [true, false]
  }

  def "test call with parent complete separate thread"() {
    setup:
    final promise = newPromise()

    when:
    runUnderTrace("parent") {
      def mapped = map(promise) { "$it" }
      onComplete(mapped) {
        assert it == "$value"
        runUnderTrace("callback") {}
      }
      Thread.start {
        complete(promise, value)
      }.join()
    }

    then:
    get(promise) == value
    assertTraces(1) {
      trace(0, 2) {
        basicSpan(it, 0, "parent")
        basicSpan(it, 1, "callback", it.span(0))
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
      runUnderTrace("callback") {}
    }

    runUnderTrace("other") {
      complete(promise, value)
    }

    then:
    get(promise) == value
    assertTraces(1) {
      trace(0, 2) {
        // TODO: is this really the behavior we want?
        basicSpan(it, 0, "other")
        basicSpan(it, 1, "callback", it.span(0))
      }
    }

    where:
    value << [true, false]
  }
}
