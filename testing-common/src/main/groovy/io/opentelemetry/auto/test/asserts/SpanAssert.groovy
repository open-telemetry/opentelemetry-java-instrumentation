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
import static io.opentelemetry.auto.test.asserts.EventAssert.assertEvent

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import io.opentelemetry.common.AttributeConsumer
import io.opentelemetry.common.AttributeKey
import io.opentelemetry.common.ReadableAttributes
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.trace.Span
import io.opentelemetry.trace.SpanId
import io.opentelemetry.trace.Status
import io.opentelemetry.trace.attributes.SemanticAttributes
import java.util.regex.Pattern

class SpanAssert {
  private final SpanData span
  private final checked = [:]

  private final Set<Integer> assertedEventIndexes = new HashSet<>()

  private SpanAssert(span) {
    this.span = span
  }

  static void assertSpan(SpanData span,
                         @ClosureParams(value = SimpleType, options = ['io.opentelemetry.auto.test.asserts.SpanAssert'])
                         @DelegatesTo(value = SpanAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    def asserter = new SpanAssert(span)
    asserter.assertSpan spec
    asserter.assertEventsAllVerified()
  }

  void assertSpan(
    @ClosureParams(value = SimpleType, options = ['io.opentelemetry.auto.test.asserts.SpanAssert'])
    @DelegatesTo(value = SpanAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    def clone = (Closure) spec.clone()
    clone.delegate = this
    clone.resolveStrategy = Closure.DELEGATE_FIRST
    clone(this)
    assertDefaults()
  }

  void event(int index, @ClosureParams(value = SimpleType, options = ['io.opentelemetry.auto.test.asserts.EventAssert']) @DelegatesTo(value = EventAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    if (index >= span.events.size()) {
      throw new ArrayIndexOutOfBoundsException(index)
    }
    assertedEventIndexes.add(index)
    assertEvent(span.events.get(index), spec)
  }

  def assertSpanNameContains(String spanName, String... shouldContainArr) {
    for (String shouldContain : shouldContainArr) {
      assert spanName.contains(shouldContain)
    }
  }

  def operationName(String name) {
    assert span.name == name
    checked.name = true
  }

  def operationName(Pattern pattern) {
    assert span.name =~ pattern
    checked.name = true
  }

  def operationName(Closure spec) {
    assert ((Closure) spec).call(span.name)
    checked.name = true
  }

  def operationNameContains(String... operationNameParts) {
    assertSpanNameContains(span.name, operationNameParts)
    checked.name = true
  }

  def spanKind(Span.Kind spanKind) {
    assert span.kind == spanKind
    checked.kind = true
  }

  def parent() {
    assert !SpanId.isValid(span.parentSpanId)
    checked.parentSpanId = true
  }

  def parentId(String parentId) {
    assert span.parentSpanId == parentId
    checked.parentId = true
  }

  def traceId(String traceId) {
    assert span.traceId == traceId
    checked.traceId = true
  }

  def childOf(SpanData parent) {
    parentId(parent.spanId)
    traceId(parent.traceId)
  }

  def hasLink(SpanData linked) {
    hasLink(linked.traceId, linked.spanId)
  }

  def hasLink(String traceId, String spanId) {
    def found = false
    for (def link : span.links) {
      if (link.context.traceIdAsHexString == traceId && link.context.spanIdAsHexString == spanId) {
        found = true
        break
      }
    }
    assert found
  }

  def status(Status status) {
    assert span.status == status
    checked.status = true
  }

  def errored(boolean errored) {
    if (errored) {
      assert span.status != Status.OK
    } else {
      assert span.status == Status.OK
    }
    checked.status = true
  }

  def errorEvent(Class<Throwable> errorType) {
    errorEvent(errorType, null)
  }

  def errorEvent(Class<Throwable> errorType, message) {
    errorEvent(errorType, message, 0)
  }

  def errorEvent(Class<Throwable> errorType, message, int index) {
    event(index) {
      eventName(SemanticAttributes.EXCEPTION_EVENT_NAME)
      attributes {
        "${SemanticAttributes.EXCEPTION_TYPE.key()}" errorType.canonicalName
        "${SemanticAttributes.EXCEPTION_STACKTRACE.key()}" String
        if (message != null) {
          "${SemanticAttributes.EXCEPTION_MESSAGE.key()}" message
        }
      }
    }
  }

  void assertDefaults() {
    if (!checked.status) {
      errored(false)
    }
  }

  void attributes(@ClosureParams(value = SimpleType, options = ['io.opentelemetry.auto.test.asserts.AttributesAssert'])
                  @DelegatesTo(value = AttributesAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    assertAttributes(toMap(span.attributes), spec)
  }

  void assertEventsAllVerified() {
    assert assertedEventIndexes.size() == span.events.size()
  }

  private Map<String, Object> toMap(ReadableAttributes attributes) {
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
