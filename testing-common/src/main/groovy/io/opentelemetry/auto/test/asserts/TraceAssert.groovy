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

import com.google.common.base.Stopwatch
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import io.opentelemetry.auto.test.InMemoryExporter
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.trace.TraceId
import java.util.concurrent.TimeUnit

import static SpanAssert.assertSpan

class TraceAssert {
  private final List<SpanData> spans

  private final Set<Integer> assertedIndexes = new HashSet<>()

  private TraceAssert(spans) {
    this.spans = spans
  }

  static void assertTrace(InMemoryExporter writer, TraceId traceId, int expectedSize,
                          @ClosureParams(value = SimpleType, options = ['io.opentelemetry.auto.test.asserts.TraceAssert'])
                          @DelegatesTo(value = TraceAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    def spans = getTrace(writer, traceId)
    Stopwatch stopwatch = Stopwatch.createStarted()
    while (spans.size() < expectedSize && stopwatch.elapsed(TimeUnit.SECONDS) < 10) {
      Thread.sleep(10)
      spans = getTrace(writer, traceId)
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

  void span(int index, @ClosureParams(value = SimpleType, options = ['io.opentelemetry.auto.test.asserts.SpanAssert']) @DelegatesTo(value = SpanAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    if (index >= spans.size()) {
      throw new ArrayIndexOutOfBoundsException(index)
    }
    assertedIndexes.add(index)
    assertSpan(spans.get(index), spec)
  }

  void span(String name, @ClosureParams(value = SimpleType, options = ['io.opentelemetry.auto.test.asserts.SpanAssert']) @DelegatesTo(value = SpanAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
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

  private static List<SpanData> getTrace(InMemoryExporter writer, TraceId traceId) {
    for (List<SpanData> trace : writer.getTraces()) {
      if (trace[0].traceId == traceId) {
        return trace
      }
    }
    throw new AssertionError("Trace not found: " + traceId)
  }
}
