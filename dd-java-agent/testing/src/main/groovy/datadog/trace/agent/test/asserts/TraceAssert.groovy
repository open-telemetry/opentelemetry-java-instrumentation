package datadog.trace.agent.test.asserts

import com.google.common.base.Stopwatch
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import io.opentelemetry.sdk.trace.SpanData

import java.util.concurrent.TimeUnit

import static SpanAssert.assertSpan

class TraceAssert {
  private final List<SpanData> trace
  private final int size
  private final Set<Integer> assertedIndexes = new HashSet<>()

  private TraceAssert(trace) {
    this.trace = trace
    size = trace.size()
  }

  static void assertTrace(List<SpanData> trace, int expectedSize,
                          @ClosureParams(value = SimpleType, options = ['datadog.trace.agent.test.asserts.TraceAssert'])
                          @DelegatesTo(value = TraceAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    Stopwatch stopwatch = Stopwatch.createStarted()
    while (stopwatch.elapsed(TimeUnit.SECONDS) < 10) {
      if (trace.size() == expectedSize) {
        break
      }
      Thread.sleep(10)
    }
    assert trace.size() == expectedSize
    def asserter = new TraceAssert(trace)
    def clone = (Closure) spec.clone()
    clone.delegate = asserter
    clone.resolveStrategy = Closure.DELEGATE_FIRST
    clone(asserter)
    asserter.assertSpansAllVerified()
  }

  SpanData span(int index) {
    trace.get(index)
  }

  void span(int index, @ClosureParams(value = SimpleType, options = ['datadog.trace.agent.test.asserts.SpanAssert']) @DelegatesTo(value = SpanAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    if (index >= size) {
      throw new ArrayIndexOutOfBoundsException(index)
    }
    if (trace.size() != size) {
      throw new ConcurrentModificationException("Trace modified during assertion")
    }
    assertedIndexes.add(index)
    assertSpan(trace.get(index), spec)
  }

  void span(String name, @ClosureParams(value = SimpleType, options = ['datadog.trace.agent.test.asserts.SpanAssert']) @DelegatesTo(value = SpanAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    int index = -1
    for (int i = 0; i < trace.size(); i++) {
      if (trace[i].name == name) {
        index = i
        break
      }
    }
    span(index, spec)
  }

  void assertSpansAllVerified() {
    assert assertedIndexes.size() == size
  }
}
