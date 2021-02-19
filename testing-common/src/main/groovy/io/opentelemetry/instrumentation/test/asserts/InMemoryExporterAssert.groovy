/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.test.asserts

import static TraceAssert.assertTrace

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil
import io.opentelemetry.sdk.trace.data.SpanData
import org.codehaus.groovy.runtime.powerassert.PowerAssertionError
import org.spockframework.runtime.Condition
import org.spockframework.runtime.ConditionNotSatisfiedError
import org.spockframework.runtime.model.TextPosition

import java.util.function.Supplier

class InMemoryExporterAssert {
  private final List<List<SpanData>> traces
  private final Supplier<List<SpanData>> spanSupplier

  private final Set<Integer> assertedIndexes = new HashSet<>()

  private InMemoryExporterAssert(List<List<SpanData>> traces, Supplier<List<SpanData>> spanSupplier) {
    this.traces = traces
    this.spanSupplier = spanSupplier
  }

  static void assertTraces(Supplier<List<SpanData>> spanSupplier, int expectedSize,
                           @ClosureParams(value = SimpleType, options = ['io.opentelemetry.instrumentation.test.asserts.ListWriterAssert'])
                           @DelegatesTo(value = InMemoryExporterAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    try {
      def traces = TelemetryDataUtil.waitForTraces(spanSupplier, expectedSize)
      assert traces.size() == expectedSize
      def asserter = new InMemoryExporterAssert(traces, spanSupplier)
      def clone = (Closure) spec.clone()
      clone.delegate = asserter
      clone.resolveStrategy = Closure.DELEGATE_FIRST
      clone(asserter)
      asserter.assertTracesAllVerified()
    } catch (PowerAssertionError e) {
      def stackLine = null
      for (int i = 0; i < e.stackTrace.length; i++) {
        def className = e.stackTrace[i].className
        def skip = className.startsWith("org.codehaus.groovy.") ||
          className.startsWith("io.opentelemetry.instrumentation.test.") ||
          className.startsWith("sun.reflect.") ||
          className.startsWith("groovy.lang.") ||
          className.startsWith("java.lang.")
        if (skip) {
          continue
        }
        stackLine = e.stackTrace[i]
        break
      }
      def condition = new Condition(null, "$stackLine", TextPosition.create(stackLine == null ? 0 : stackLine.lineNumber, 0), e.message, null, e)
      throw new ConditionNotSatisfiedError(condition, e)
    }
  }

  List<List<SpanData>> getTraces() {
    return traces
  }

  void trace(int index, int expectedSize,
             @ClosureParams(value = SimpleType, options = ['io.opentelemetry.instrumentation.test.asserts.TraceAssert'])
             @DelegatesTo(value = TraceAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    if (index >= traces.size()) {
      throw new ArrayIndexOutOfBoundsException(index)
    }
    assertedIndexes.add(index)
    assertTrace(spanSupplier, traces[index][0].traceId, expectedSize, spec)
  }

  // this doesn't provide any functionality, just a self-documenting marker
  static void sortTraces(Closure callback) {
    callback.call()
  }

  void assertTracesAllVerified() {
    assert assertedIndexes.size() == traces.size()
  }

  void sortSpansByStartTime() {
    traces.each {
      it.sort { a, b ->
        return a.startEpochNanos <=> b.startEpochNanos
      }
    }
  }
}
