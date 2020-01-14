package io.opentelemetry.auto.test.asserts

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import io.opentelemetry.auto.test.ListWriter
import io.opentelemetry.sdk.trace.SpanData
import org.codehaus.groovy.runtime.powerassert.PowerAssertionError
import org.spockframework.runtime.Condition
import org.spockframework.runtime.ConditionNotSatisfiedError
import org.spockframework.runtime.model.TextPosition

import static TraceAssert.assertTrace

class ListWriterAssert {
  private final List<List<SpanData>> traces
  private final ListWriter writer

  private final Set<Integer> assertedIndexes = new HashSet<>()

  private ListWriterAssert(List<List<SpanData>> traces, ListWriter writer) {
    this.traces = traces
    this.writer = writer
  }

  static void assertTraces(ListWriter writer, int expectedSize,
                           @ClosureParams(value = SimpleType, options = ['io.opentelemetry.auto.test.asserts.ListWriterAssert'])
                           @DelegatesTo(value = ListWriterAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    try {
      writer.waitForTraces(expectedSize)
      def traces = new ArrayList<>(writer.traces)
      assert traces.size() == expectedSize
      def asserter = new ListWriterAssert(traces, writer)
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
          className.startsWith("io.opentelemetry.auto.test.") ||
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
             @ClosureParams(value = SimpleType, options = ['io.opentelemetry.auto.test.asserts.TraceAssert'])
             @DelegatesTo(value = TraceAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    if (index >= traces.size()) {
      throw new ArrayIndexOutOfBoundsException(index)
    }
    assertedIndexes.add(index)
    assertTrace(writer, traces[index][0].traceId, expectedSize, spec)
  }

  // this doesn't provide any functionality, just a self-documenting marker
  void sortTraces(Closure callback) {
    callback.call()
  }

  void assertTracesAllVerified() {
    assert assertedIndexes.size() == traces.size()
  }
}
