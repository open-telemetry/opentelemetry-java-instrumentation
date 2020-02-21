package io.opentelemetry.auto.test.asserts

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import io.opentelemetry.sdk.trace.SpanData.TimedEvent

import static TagsAssert.assertTags

class EventAssert {
  private final TimedEvent event
  private final checked = [:]

  private EventAssert(event) {
    this.event = event
  }

  static void assertEvent(TimedEvent event,
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

  def name(String name) {
    assert event.name == name
    checked.name = true
  }

  void attributes(@ClosureParams(value = SimpleType, options = ['io.opentelemetry.auto.test.asserts.TagsAssert'])
                  @DelegatesTo(value = TagsAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    assertTags(event.attributes, spec)
  }
}
