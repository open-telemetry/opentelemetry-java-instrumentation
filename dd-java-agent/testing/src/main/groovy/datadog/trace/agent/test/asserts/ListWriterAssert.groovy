package datadog.trace.agent.test.asserts

import datadog.opentracing.DDSpan
import datadog.trace.common.writer.ListWriter
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import org.codehaus.groovy.runtime.powerassert.PowerAssertionError
import org.spockframework.runtime.Condition
import org.spockframework.runtime.ConditionNotSatisfiedError
import org.spockframework.runtime.model.TextPosition

import static TraceAssert.assertTrace

class ListWriterAssert {
  private final ListWriter writer
  private final int size
  private final Set<Integer> assertedIndexes = new HashSet<>()

  private ListWriterAssert(ListWriter writer) {
    this.writer = writer
    size = writer.size()
  }

  static void assertTraces(ListWriter writer, int expectedSize,
                           @ClosureParams(value = SimpleType, options = ['datadog.trace.agent.test.asserts.ListWriterAssert'])
                           @DelegatesTo(value = ListWriterAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    try {
      writer.waitForTraces(expectedSize)
      assert writer.size() == expectedSize
      def asserter = new ListWriterAssert(writer)
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
          className.startsWith("datadog.trace.agent.test.") ||
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

  List<DDSpan> trace(int index) {
    return writer.get(index)
  }

  void trace(int index, int expectedSize,
             @ClosureParams(value = SimpleType, options = ['datadog.trace.agent.test.asserts.TraceAssert'])
             @DelegatesTo(value = TraceAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    if (index >= size) {
      throw new ArrayIndexOutOfBoundsException(index)
    }
    if (writer.size() != size) {
      throw new ConcurrentModificationException("ListWriter modified during assertion")
    }
    assertedIndexes.add(index)
    assertTrace(writer.get(index), expectedSize, spec)
  }

  void assertTracesAllVerified() {
    assert assertedIndexes.size() == size
  }
}
