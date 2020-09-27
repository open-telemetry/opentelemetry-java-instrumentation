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

package io.opentelemetry.auto.test.asserts

import static AttributesAssert.assertAttributes

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import io.opentelemetry.common.AttributeConsumer
import io.opentelemetry.common.AttributeKey
import io.opentelemetry.common.Attributes
import io.opentelemetry.trace.Event

class EventAssert {
  private final Event event
  private final checked = [:]

  private EventAssert(event) {
    this.event = event
  }

  static void assertEvent(Event event,
                          @ClosureParams(value = SimpleType, options = ['io.opentelemetry.auto.test.asserts.EventAssert'])
                          @DelegatesTo(value = EventAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    def asserter = new EventAssert(event)
    asserter.assertEvent spec
  }

  void assertEvent(
    @ClosureParams(value = SimpleType, options = ['io.opentelemetry.auto.test.asserts.EventAssert'])
    @DelegatesTo(value = EventAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    def clone = (Closure) spec.clone()
    clone.delegate = this
    clone.resolveStrategy = Closure.DELEGATE_FIRST
    clone(this)
  }

  def eventName(String name) {
    assert event.name == name
    checked.name = true
  }

  void attributes(@ClosureParams(value = SimpleType, options = ['io.opentelemetry.auto.test.asserts.AttributesAssert'])
                  @DelegatesTo(value = AttributesAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    assertAttributes(toMap(event.attributes), spec)
  }


  private Map<String, Object> toMap(Attributes attributes) {
    def map = new HashMap()
    attributes.forEach(new AttributeConsumer() {
      @Override
      <T> void consume(AttributeKey<T> key, T value) {
        map.put(key.key, value)
      }
    })
    return map
  }
}
