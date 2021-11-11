/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.test.asserts

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanId
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes

import java.util.regex.Pattern

import static AttributesAssert.assertAttributes
import static io.opentelemetry.instrumentation.test.asserts.EventAssert.assertEvent

class SpanAssert {
  private final SpanData span
  private final checked = [:]

  private final Set<Integer> assertedEventIndexes = new HashSet<>()

  private SpanAssert(span) {
    this.span = span
  }

  static void assertSpan(SpanData span,
                         @ClosureParams(value = SimpleType, options = ['io.opentelemetry.instrumentation.test.asserts.SpanAssert'])
                         @DelegatesTo(value = SpanAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    def asserter = new SpanAssert(span)
    asserter.assertSpan spec
    asserter.assertEventsAllVerified()
  }

  void assertSpan(
    @ClosureParams(value = SimpleType, options = ['io.opentelemetry.instrumentation.test.asserts.SpanAssert'])
    @DelegatesTo(value = SpanAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    def clone = (Closure) spec.clone()
    clone.delegate = this
    clone.resolveStrategy = Closure.DELEGATE_FIRST
    clone(this)
    assertDefaults()
  }

  void events(int expectedCount) {
    assert span.totalRecordedEvents == expectedCount
    assert span.events.size() == expectedCount
  }

  void event(int index, @ClosureParams(value = SimpleType, options = ['io.opentelemetry.instrumentation.test.asserts.EventAssert']) @DelegatesTo(value = EventAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    if (index >= span.events.size()) {
      throw new ArrayIndexOutOfBoundsException(index)
    }
    assertedEventIndexes.add(index)
    assertEvent(span.events.get(index), spec)
  }

  def name(String expected) {
    assert span.name == expected
    checked.name = true
  }

  def instrumentationLibraryVersion(String expected) {
    assert span.instrumentationLibraryInfo.version == expected
    checked.instrumentationLibraryVersion = true
  }

  def name(Pattern expected) {
    assert span.name =~ expected
    checked.name = true
  }

  def name(Closure expected) {
    assert ((Closure) expected).call(span.name)
    checked.name = true
  }

  def nameContains(String... expectedParts) {
    for (String expectedPart : expectedParts) {
      assert span.name.contains(expectedPart)
    }
    checked.name = true
  }

  def kind(SpanKind expected) {
    assert span.kind == expected
    checked.kind = true
  }

  def hasNoParent() {
    assert !SpanId.isValid(span.parentSpanId)
    checked.parentSpanId = true
  }

  def parentSpanId(String expected) {
    assert span.parentSpanId == expected
    checked.parentId = true
  }

  def traceId(String expected) {
    assert span.traceId == expected
    checked.traceId = true
  }

  def childOf(SpanData expectedParent) {
    parentSpanId(expectedParent.spanId)
    traceId(expectedParent.traceId)
  }

  def hasLink(SpanData expectedLink) {
    hasLink(expectedLink.traceId, expectedLink.spanId)
  }

  def hasLink(String expectedTraceId, String expectedSpanId) {
    def found = false
    for (def link : span.links) {
      if (link.spanContext.traceId == expectedTraceId && link.spanContext.spanId == expectedSpanId) {
        found = true
        break
      }
    }
    assert found
  }

  def hasNoLinks() {
    assert span.links.empty
  }

  def status(StatusCode expected) {
    assert span.status.statusCode == expected
    checked.status = true
  }

  def errorEvent(Class<Throwable> expectedClass) {
    errorEvent(expectedClass, null)
  }

  def errorEvent(Class<Throwable> expectedClass, expectedMessage) {
    errorEvent(expectedClass, expectedMessage, 0)
  }

  def errorEvent(Class<Throwable> errorClass, expectedMessage, int index) {
    event(index) {
      eventName(SemanticAttributes.EXCEPTION_EVENT_NAME)
      attributes {
        "${SemanticAttributes.EXCEPTION_TYPE.key}" errorClass.canonicalName
        "${SemanticAttributes.EXCEPTION_STACKTRACE.key}" String
        if (expectedMessage != null) {
          "${SemanticAttributes.EXCEPTION_MESSAGE.key}" expectedMessage
        }
      }
    }
  }

  void assertDefaults() {
    if (!checked.status) {
      status(StatusCode.UNSET)
    }
    if (!checked.kind) {
      kind(SpanKind.INTERNAL)
    }
  }

  void attributes(@ClosureParams(value = SimpleType, options = ['io.opentelemetry.instrumentation.test.asserts.AttributesAssert'])
                  @DelegatesTo(value = AttributesAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    assertAttributes(toMap(span.attributes), spec)
  }

  void assertEventsAllVerified() {
    assert assertedEventIndexes.size() == span.events.size()
  }

  private Map<String, Object> toMap(Attributes attributes) {
    def map = new HashMap()
    attributes.forEach { key, value ->
      map.put(key.key, value)
    }
    return map
  }
}
