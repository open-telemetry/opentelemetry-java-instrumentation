package datadog.trace.agent.test.asserts

import datadog.opentracing.DDSpan
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType

import static TagsAssert.assertTags

class SpanAssert {
  private final DDSpan span

  private SpanAssert(span) {
    this.span = span
  }

  static void assertSpan(DDSpan span,
                         @ClosureParams(value = SimpleType, options = ['datadog.trace.agent.test.asserts.SpanAssert'])
                         @DelegatesTo(value = SpanAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    def asserter = new SpanAssert(span)
    def clone = (Closure) spec.clone()
    clone.delegate = asserter
    clone.resolveStrategy = Closure.DELEGATE_FIRST
    clone(asserter)
  }

  def assertSpanNameContains(String spanName, String... shouldContainArr) {
    for (String shouldContain : shouldContainArr) {
      assert spanName.contains(shouldContain)
    }
  }

  def serviceName(String name) {
    assert span.serviceName == name
  }

  def operationName(String name) {
    assert span.operationName == name
  }

  def operationNameContains(String... operationNameParts) {
    assertSpanNameContains(span.operationName, operationNameParts)
  }

  def resourceName(String name) {
    assert span.resourceName == name
  }

  def resourceNameContains(String... resourceNameParts) {
    assertSpanNameContains(span.resourceName, resourceNameParts)
  }

  def spanType(String type) {
    assert span.spanType == type
    assert span.tags["span.type"] == type
  }

  def parent() {
    assert span.parentId == "0"
  }

  def parentId(String parentId) {
    assert span.parentId == parentId
  }

  def traceId(String traceId) {
    assert span.traceId == traceId
  }

  def childOf(DDSpan parent) {
    assert span.parentId == parent.spanId
    assert span.traceId == parent.traceId
  }

  def errored(boolean errored) {
    assert span.isError() == errored
  }

  void tags(@ClosureParams(value = SimpleType, options = ['datadog.trace.agent.test.asserts.TagsAssert'])
            @DelegatesTo(value = TagsAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    assertTags(span, spec)
  }
}
