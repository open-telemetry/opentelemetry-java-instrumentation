/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.rxjava2.TracedWithSpan
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.reactivex.Completable
import io.reactivex.observers.TestObserver
import io.reactivex.subjects.CompletableSubject

class RxJava2WithSpanInstrumentationTest extends AgentInstrumentationSpecification implements AgentTestTrait {

  def "should capture span for already completed Completable"() {
    setup:
    def observer = new TestObserver()
    def source = Completable.complete()
    def result = new TracedWithSpan()
      .completable(source)
    result.subscribe(observer)
    observer.assertComplete()

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.completable"
          kind SpanKind.INTERNAL
          hasNoParent()
          errored false
          attributes {
          }
        }
      }
    }
  }

  def "should capture span for eventually completed Completable"() {
    setup:
    def source = CompletableSubject.create()
    def observer = new TestObserver()
    new TracedWithSpan()
      .completable(source)
      .subscribe(observer)
    observer.assertSubscribed()

    expect:
    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}

    source.onComplete()
    observer.assertComplete()

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.completable"
          kind SpanKind.INTERNAL
          hasNoParent()
          errored false
          attributes {
          }
        }
      }
    }
  }
}
