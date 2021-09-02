/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.test.asserts

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.trace.data.EventData

import static AttributesAssert.assertAttributes

class EventAssert {
  private final EventData event
  private final checked = [:]

  private EventAssert(event) {
    this.event = event
  }

  static void assertEvent(EventData event,
                          @ClosureParams(value = SimpleType, options = ['io.opentelemetry.instrumentation.test.asserts.EventAssert'])
                          @DelegatesTo(value = EventAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    def asserter = new EventAssert(event)
    asserter.assertEvent spec
  }

  void assertEvent(
    @ClosureParams(value = SimpleType, options = ['io.opentelemetry.instrumentation.test.asserts.EventAssert'])
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

  void attributes(@ClosureParams(value = SimpleType, options = ['io.opentelemetry.instrumentation.test.asserts.AttributesAssert'])
                  @DelegatesTo(value = AttributesAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    assertAttributes(toMap(event.attributes), spec)
  }


  private Map<String, Object> toMap(Attributes attributes) {
    def map = new HashMap()
    attributes.forEach { key, value ->
      map.put(key.key, value)
    }
    return map
  }
}
