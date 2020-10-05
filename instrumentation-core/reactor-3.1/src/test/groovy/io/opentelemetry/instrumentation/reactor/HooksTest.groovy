/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.reactor

import io.opentelemetry.auto.test.InstrumentationTestRunner
import java.util.concurrent.atomic.AtomicReference
import reactor.core.CoreSubscriber
import reactor.core.publisher.Mono

class HooksTest extends InstrumentationTestRunner {

  def "can reset out hooks"() {
    setup:
    AtomicReference<CoreSubscriber> subscriber = new AtomicReference<>()

    when: "no hook registered"
    new CapturingMono(subscriber).map { it + 1 }.subscribe()

    then:
    !(subscriber.get() instanceof TracingSubscriber)

    when: "hook registered"
    TracingOperator.registerOnEachOperator()
    new CapturingMono(subscriber).map { it + 1 }.subscribe()

    then:
    subscriber.get() instanceof TracingSubscriber

    when: "hook reset"
    TracingOperator.resetOnEachOperator()
    new CapturingMono(subscriber).map { it + 1 }.subscribe()

    then:
    !(subscriber.get() instanceof TracingSubscriber)
  }

  private static class CapturingMono extends Mono<Integer> {
    final AtomicReference subscriber

    CapturingMono(AtomicReference subscriber) {
      this.subscriber = subscriber
    }

    @Override
    void subscribe(CoreSubscriber<? super Integer> actual) {
      subscriber.set(actual.actual) // debug showed this is the right way to do
    }
  }
}
