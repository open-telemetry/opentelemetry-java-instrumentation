package datadog.trace.agent.test

import datadog.opentracing.DDSpan

import static datadog.trace.agent.test.SpanAssert.assertSpan

class TraceAssert {
  private final List<DDSpan> trace
  private final int size
  private final Set<Integer> assertedIndexes = new HashSet<>()

  private TraceAssert(trace) {
    this.trace = trace
    size = trace.size()
  }

  static void assertTrace(List<DDSpan> trace, int expectedSize,
                          @DelegatesTo(value = File, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    assert trace.size() == expectedSize
    def asserter = new TraceAssert(trace)
    def clone = (Closure) spec.clone()
    clone.delegate = asserter
    clone.resolveStrategy = Closure.DELEGATE_FIRST
    clone(asserter)
    asserter.assertSpansAllVerified()
    asserter
  }

  DDSpan span(int index) {
    trace.get(index)
  }

  void span(int index, @DelegatesTo(value = SpanAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    if (index >= size) {
      throw new ArrayIndexOutOfBoundsException(index)
    }
    if (trace.size() != size) {
      throw new ConcurrentModificationException("Trace modified during assertion")
    }
    assertedIndexes.add(index)
    assertSpan(trace.get(index), spec)
  }

  void assertSpansAllVerified() {
    assert assertedIndexes.size() == size
  }
}
