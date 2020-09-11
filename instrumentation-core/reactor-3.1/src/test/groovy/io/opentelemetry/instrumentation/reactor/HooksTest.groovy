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
