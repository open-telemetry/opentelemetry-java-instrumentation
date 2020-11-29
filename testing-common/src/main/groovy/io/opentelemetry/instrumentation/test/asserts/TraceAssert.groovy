/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.test.asserts

import static SpanAssert.assertSpan

import com.google.common.base.Stopwatch
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import io.opentelemetry.sdk.trace.data.SpanData
import java.util.concurrent.TimeUnit

class TraceAssert {
  private final List<SpanData> spans

  private final Set<Integer> assertedIndexes = new HashSet<>()

  private TraceAssert(spans) {
    this.spans = spans
  }

  static void assertTrace(List<List<SpanData>> traces, String traceId, int expectedSize,
                          @ClosureParams(value = SimpleType, options = ['io.opentelemetry.instrumentation.test.asserts.TraceAssert'])
                          @DelegatesTo(value = TraceAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    def spans = getTrace(traces, traceId)
    Stopwatch stopwatch = Stopwatch.createStarted()
    while (spans.size() < expectedSize && stopwatch.elapsed(TimeUnit.SECONDS) < 10) {
      Thread.sleep(10)
      spans = getTrace(traces, traceId)
    }
    assert spans.size() == expectedSize
    def asserter = new TraceAssert(spans)
    def clone = (Closure) spec.clone()
    clone.delegate = asserter
    clone.resolveStrategy = Closure.DELEGATE_FIRST
    clone(asserter)
    asserter.assertSpansAllVerified()
  }

  List<SpanData> getSpans() {
    return spans
  }

  SpanData span(int index) {
    spans.get(index)
  }

  void span(int index, @ClosureParams(value = SimpleType, options = ['io.opentelemetry.instrumentation.test.asserts.SpanAssert']) @DelegatesTo(value = SpanAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    if (index >= spans.size()) {
      throw new ArrayIndexOutOfBoundsException(index)
    }
    assertedIndexes.add(index)
    assertSpan(spans.get(index), spec)
  }

  void span(String name, @ClosureParams(value = SimpleType, options = ['io.opentelemetry.instrumentation.test.asserts.SpanAssert']) @DelegatesTo(value = SpanAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    int index = -1
    for (int i = 0; i < spans.size(); i++) {
      if (spans[i].name == name) {
        index = i
        break
      }
    }
    span(index, spec)
  }

  // this doesn't provide any functionality, just a self-documenting marker
  void sortSpans(Closure callback) {
    callback.call()
  }

  void assertSpansAllVerified() {
    assert assertedIndexes.size() == spans.size()
  }

  private static List<SpanData> getTrace(List<List<SpanData>> traces, String traceId) {
    for (List<SpanData> trace : traces) {
      if (trace[0].traceId == traceId) {
        return trace
      }
    }
    throw new AssertionError("Trace not found: " + traceId)
  }
}
