/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.test.asserts

import static SpanAssert.assertSpan

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil
import io.opentelemetry.sdk.trace.data.SpanData
import java.util.concurrent.TimeUnit
import java.util.function.Supplier

class TraceAssert {
  private final List<SpanData> spans

  private final Set<Integer> assertedIndexes = new HashSet<>()

  private TraceAssert(spans) {
    this.spans = spans
  }

  static void assertTrace(Supplier<List<SpanData>> spanSupplier, String traceId, int expectedSize,
                          @ClosureParams(value = SimpleType, options = ['io.opentelemetry.instrumentation.test.asserts.TraceAssert'])
                          @DelegatesTo(value = TraceAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    def spans = getTrace(spanSupplier, traceId)
    def startTime = System.nanoTime()
    while (spans.size() < expectedSize && elapsedSeconds(startTime) < 10) {
      Thread.sleep(10)
      spans = getTrace(spanSupplier, traceId)
    }
    assert spans.size() == expectedSize
    def asserter = new TraceAssert(spans)
    def clone = (Closure) spec.clone()
    clone.delegate = asserter
    clone.resolveStrategy = Closure.DELEGATE_FIRST
    clone(asserter)
    asserter.assertSpansAllVerified()
  }

  private static long elapsedSeconds(long startTime) {
    TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime)
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

  void assertSpansAllVerified() {
    assert assertedIndexes.size() == spans.size()
  }

  private static List<SpanData> getTrace(Supplier<List<SpanData>> spanSupplier, String traceId) {
    List<List<SpanData>> traces = TelemetryDataUtil.groupTraces(spanSupplier.get())
    for (List<SpanData> trace : traces) {
      if (trace[0].traceId == traceId) {
        return trace
      }
    }
    throw new AssertionError("Trace not found: " + traceId)
  }
}
