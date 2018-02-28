package datadog.opentracing

import datadog.trace.common.writer.ListWriter
import spock.lang.Specification
import spock.lang.Subject


class TraceCollectionTest extends Specification {
  def writer = new ListWriter()
  def tracer = new DDTracer(writer)

  def traceId = System.identityHashCode(this)

  @Subject
  TraceCollection trace = new TraceCollection(tracer, traceId)

  DDSpan rootSpan = SpanFactory.newSpanOf(trace)

  def setup() {
    assert trace.size() == 0
    assert trace.unfinishedSpanCount.get() == 1
    assert trace.weakReferences.size() == 1
    assert trace.isWritten.get() == false
  }

  def "single span gets added to trace and written when finished"() {
    setup:
    rootSpan.finish()

    expect:
    trace.asList() == [rootSpan]
    writer == [[rootSpan]]
  }

  def "child finishes before parent"() {
    when:
    def child = tracer.buildSpan("child").asChildOf(rootSpan).start()

    then:
    trace.unfinishedSpanCount.get() == 2
    trace.weakReferences.size() == 2

    when:
    child.finish()

    then:
    trace.unfinishedSpanCount.get() == 1
    trace.weakReferences.size() == 1
    trace.asList() == [child]
    writer == []

    when:
    rootSpan.finish()

    then:
    trace.unfinishedSpanCount.get() == 0
    trace.weakReferences.size() == 0
    trace.asList() == [rootSpan, child]
    writer == [[rootSpan, child]]
  }

  def "parent finishes before child which holds up trace"() {
    when:
    def child = tracer.buildSpan("child").asChildOf(rootSpan).start()

    then:
    trace.unfinishedSpanCount.get() == 2
    trace.weakReferences.size() == 2

    when:
    rootSpan.finish()

    then:
    trace.unfinishedSpanCount.get() == 1
    trace.weakReferences.size() == 1
    trace.asList() == [rootSpan]
    writer == []

    when:
    child.finish()

    then:
    trace.unfinishedSpanCount.get() == 0
    trace.weakReferences.size() == 0
    trace.asList() == [child, rootSpan]
    writer == [[child, rootSpan]]
  }

  def "trace reported when unfinished child discarded"() {
    when:
    def child = tracer.buildSpan("child").asChildOf(rootSpan).start()
    rootSpan.finish()

    then:
    trace.unfinishedSpanCount.get() == 1
    trace.weakReferences.size() == 1
    trace.asList() == [rootSpan]
    writer == []

    when:
    child = null
    while (!trace.clean()) {
      trace.awaitGC()
    }

    then:
    trace.unfinishedSpanCount.get() == 0
    trace.weakReferences.size() == 0
    trace.asList() == [rootSpan]
    writer == [[rootSpan]]
  }

  def "add unfinished span to trace fails"() {
    setup:
    trace.addSpan(rootSpan)

    expect:
    trace.unfinishedSpanCount.get() == 1
    trace.weakReferences.size() == 1
    trace.asList() == []
  }

  def "register span to wrong trace fails"() {
    setup:
    def otherTrace = new TraceCollection(tracer, traceId - 10)
    otherTrace.registerSpan(new DDSpan(0, rootSpan.context()))

    expect:
    otherTrace.unfinishedSpanCount.get() == 0
    otherTrace.weakReferences.size() == 0
    otherTrace.asList() == []
  }

  def "add span to wrong trace fails"() {
    setup:
    def otherTrace = new TraceCollection(tracer, traceId - 10)
    rootSpan.finish()
    otherTrace.addSpan(rootSpan)

    expect:
    otherTrace.unfinishedSpanCount.get() == 0
    otherTrace.weakReferences.size() == 0
    otherTrace.asList() == []
  }
}
