package datadog.trace.agent.test.asserts

import datadog.opentracing.DDSpan
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType

import static TagsAssert.assertTags

class SpanAssert {
  private final DDSpan span
  private final checked = [:]

  private SpanAssert(span) {
    this.span = span
  }

  static void assertSpan(DDSpan span,
                         @ClosureParams(value = SimpleType, options = ['datadog.trace.agent.test.asserts.SpanAssert'])
                         @DelegatesTo(value = SpanAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    def asserter = new SpanAssert(span)
    asserter.assertSpan spec
  }

  void assertSpan(
    @ClosureParams(value = SimpleType, options = ['datadog.trace.agent.test.asserts.SpanAssert'])
    @DelegatesTo(value = SpanAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    def clone = (Closure) spec.clone()
    clone.delegate = this
    clone.resolveStrategy = Closure.DELEGATE_FIRST
    clone(this)
    assertDefaults()
  }

  def assertSpanNameContains(String spanName, String... shouldContainArr) {
    for (String shouldContain : shouldContainArr) {
      assert spanName.contains(shouldContain)
    }
  }

  def serviceName(String name) {
    assert span.serviceName == name
    checked.serviceName = true
  }

  def operationName(String name) {
    assert span.operationName == name
    checked.operationName = true
  }

  def operationNameContains(String... operationNameParts) {
    assertSpanNameContains(span.operationName, operationNameParts)
    checked.operationName = true
  }

  def resourceName(String name) {
    assert span.resourceName == name
    checked.resourceName = true
  }

  def resourceNameContains(String... resourceNameParts) {
    assertSpanNameContains(span.resourceName, resourceNameParts)
    checked.resourceName = true
  }

  def spanType(String type) {
    assert span.spanType == type
    assert span.tags["span.type"] == null
    checked.spanType = true
  }

  def parent() {
    assert span.parentId == "0"
    checked.parentId = true
  }

  def parentId(String parentId) {
    assert span.parentId == parentId
    checked.parentId = true
  }

  def traceId(String traceId) {
    assert span.traceId == traceId
    checked.traceId = true
  }

  def childOf(DDSpan parent) {
    parentId(parent.spanId)
    traceId(parent.traceId)
  }

  def errored(boolean errored) {
    assert span.isError() == errored
    checked.errored = true
  }

  void assertDefaults() {
    if (!checked.spanType) {
      spanType(null)
    }
    if (!checked.errored) {
      errored(false)
    }
  }

  void tags(@ClosureParams(value = SimpleType, options = ['datadog.trace.agent.test.asserts.TagsAssert'])
            @DelegatesTo(value = TagsAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    assertTags(span, spec)
  }
}
