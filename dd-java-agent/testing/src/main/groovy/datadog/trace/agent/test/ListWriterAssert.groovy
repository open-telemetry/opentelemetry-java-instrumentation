package datadog.trace.agent.test

import datadog.trace.common.writer.ListWriter

import static datadog.trace.agent.test.TraceAssert.assertTrace

class ListWriterAssert {
  private final ListWriter writer
  private final int size
  private final Set<Integer> assertedIndexes = new HashSet<>()

  private ListWriterAssert(writer) {
    this.writer = writer
    size = writer.size()
  }

  static ListWriterAssert assertTraces(ListWriter writer, int expectedSize,
                                       @DelegatesTo(value = ListWriterAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    writer.waitForTraces(expectedSize)
    assert writer.size() == expectedSize
    def asserter = new ListWriterAssert(writer)
    def clone = (Closure) spec.clone()
    clone.delegate = asserter
    clone.resolveStrategy = Closure.DELEGATE_FIRST
    clone(asserter)
    asserter.assertTracesAllVerified()
    asserter
  }

  TraceAssert trace(int index, int expectedSize,
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
