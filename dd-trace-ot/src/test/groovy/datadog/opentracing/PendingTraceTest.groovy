package datadog.opentracing

import datadog.trace.api.Config
import datadog.trace.common.writer.ListWriter
import datadog.trace.util.gc.GCUtils
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Timeout

import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import static datadog.trace.api.Config.PARTIAL_FLUSH_MIN_SPANS

class PendingTraceTest extends Specification {
  def traceCount = new AtomicInteger()
  def writer = new ListWriter() {
    @Override
    void incrementTraceCount() {
      PendingTraceTest.this.traceCount.incrementAndGet()
    }
  }
  def tracer = new DDTracer(writer)

  def traceId = System.identityHashCode(this)
  String traceIdStr = String.valueOf(traceId)

  @Subject
  PendingTrace trace = new PendingTrace(tracer, traceIdStr, [:])

  DDSpan rootSpan = SpanFactory.newSpanOf(trace)

  def setup() {
    assert trace.size() == 0
    assert trace.pendingReferenceCount.get() == 1
    assert trace.weakReferences.size() == 1
    assert trace.isWritten.get() == false
  }

  def "single span gets added to trace and written when finished"() {
    setup:
    rootSpan.finish()

    expect:
    trace.asList() == [rootSpan]
    writer == [[rootSpan]]
    traceCount.get() == 1
  }

  def "child finishes before parent"() {
    when:
    def child = tracer.buildSpan("child").asChildOf(rootSpan).start()

    then:
    trace.pendingReferenceCount.get() == 2
    trace.weakReferences.size() == 2

    when:
    child.finish()

    then:
    trace.pendingReferenceCount.get() == 1
    trace.weakReferences.size() == 1
    trace.asList() == [child]
    writer == []

    when:
    rootSpan.finish()

    then:
    trace.pendingReferenceCount.get() == 0
    trace.weakReferences.size() == 0
    trace.asList() == [rootSpan, child]
    writer == [[rootSpan, child]]
    traceCount.get() == 1
  }

  def "parent finishes before child which holds up trace"() {
    when:
    def child = tracer.buildSpan("child").asChildOf(rootSpan).start()

    then:
    trace.pendingReferenceCount.get() == 2
    trace.weakReferences.size() == 2

    when:
    rootSpan.finish()

    then:
    trace.pendingReferenceCount.get() == 1
    trace.weakReferences.size() == 1
    trace.asList() == [rootSpan]
    writer == []

    when:
    child.finish()

    then:
    trace.pendingReferenceCount.get() == 0
    trace.weakReferences.size() == 0
    trace.asList() == [child, rootSpan]
    writer == [[child, rootSpan]]
    traceCount.get() == 1
  }

  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  def "trace does not report when unfinished child discarded"() {
    when:
    def child = tracer.buildSpan("child").asChildOf(rootSpan).start()
    rootSpan.finish()

    then:
    trace.pendingReferenceCount.get() == 1
    trace.weakReferences.size() == 1
    trace.asList() == [rootSpan]
    writer == []

    when:
    def childRef = new WeakReference<>(child)
    child = null
    GCUtils.awaitGC(childRef)
    while (trace.pendingReferenceCount.get() > 0) {
      trace.clean()
    }

    then:
    trace.pendingReferenceCount.get() == 0
    trace.weakReferences.size() == 0
    trace.asList() == [rootSpan]
    writer == []
    traceCount.get() == 1
    !PendingTrace.SPAN_CLEANER.get().pendingTraces.contains(trace)
  }

  def "add unfinished span to trace fails"() {
    setup:
    trace.addSpan(rootSpan)

    expect:
    trace.pendingReferenceCount.get() == 1
    trace.weakReferences.size() == 1
    trace.asList() == []
    traceCount.get() == 0
  }

  def "register span to wrong trace fails"() {
    setup:
    def otherTrace = new PendingTrace(tracer, String.valueOf(traceId - 10), [:])
    otherTrace.registerSpan(new DDSpan(0, rootSpan.context()))

    expect:
    otherTrace.pendingReferenceCount.get() == 0
    otherTrace.weakReferences.size() == 0
    otherTrace.asList() == []
  }

  def "add span to wrong trace fails"() {
    setup:
    def otherTrace = new PendingTrace(tracer, String.valueOf(traceId - 10), [:])
    rootSpan.finish()
    otherTrace.addSpan(rootSpan)

    expect:
    otherTrace.pendingReferenceCount.get() == 0
    otherTrace.weakReferences.size() == 0
    otherTrace.asList() == []
  }


  def "child spans created after trace written"() {
    setup:
    rootSpan.finish()
    // this shouldn't happen, but it's possible users of the api
    // may incorrectly add spans after the trace is reported.
    // in those cases we should still decrement the pending trace count
    DDSpan childSpan = tracer.buildSpan("child").asChildOf(rootSpan).start()
    childSpan.finish()

    expect:
    trace.pendingReferenceCount.get() == 0
    trace.asList() == [rootSpan]
    writer == [[rootSpan]]
  }

  def "test getCurrentTimeNano"() {
    expect:
    // Generous 5 seconds to execute this test
    Math.abs(TimeUnit.NANOSECONDS.toSeconds(trace.currentTimeNano) - TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())) < 5
  }

  def "partial flush"() {
    when:
    def properties = new Properties()
    properties.setProperty(PARTIAL_FLUSH_MIN_SPANS, "1")
    def config = Config.get(properties)
    def tracer = new DDTracer(config, writer)
    def trace = new PendingTrace(tracer, traceIdStr, [:])
    def rootSpan = SpanFactory.newSpanOf(trace)
    def child1 = tracer.buildSpan("child1").asChildOf(rootSpan).start()
    def child2 = tracer.buildSpan("child2").asChildOf(rootSpan).start()

    then:
    trace.pendingReferenceCount.get() == 3
    trace.weakReferences.size() == 3

    when:
    rootSpan.finish()

    then:
    trace.pendingReferenceCount.get() == 2
    trace.weakReferences.size() == 2
    trace.asList() == [rootSpan]
    writer == []
    traceCount.get() == 0

    when:
    child1.finish()

    then:
    trace.pendingReferenceCount.get() == 1
    trace.weakReferences.size() == 1
    trace.asList() == [rootSpan]
    writer == [[child1]]
    traceCount.get() == 1

    when:
    child2.finish()

    then:
    trace.pendingReferenceCount.get() == 0
    trace.weakReferences.size() == 0
    trace.asList() == [child2, rootSpan]
    writer == [[child1], [child2, rootSpan]]
    traceCount.get() == 2
  }

  def "partial flush with root span closed last"() {
    when:
    def properties = new Properties()
    properties.setProperty(PARTIAL_FLUSH_MIN_SPANS, "1")
    def config = Config.get(properties)
    def tracer = new DDTracer(config, writer)
    def trace = new PendingTrace(tracer, traceIdStr, [:])
    def rootSpan = SpanFactory.newSpanOf(trace)
    def child1 = tracer.buildSpan("child1").asChildOf(rootSpan).start()
    def child2 = tracer.buildSpan("child2").asChildOf(rootSpan).start()

    then:
    trace.pendingReferenceCount.get() == 3
    trace.weakReferences.size() == 3

    when:
    child1.finish()

    then:
    trace.pendingReferenceCount.get() == 2
    trace.weakReferences.size() == 2
    trace.asList() == [child1]
    writer == []
    traceCount.get() == 0

    when:
    child2.finish()

    then:
    trace.pendingReferenceCount.get() == 1
    trace.weakReferences.size() == 1
    trace.asList() == []
    writer == [[child2, child1]]
    traceCount.get() == 1

    when:
    rootSpan.finish()

    then:
    trace.pendingReferenceCount.get() == 0
    trace.weakReferences.size() == 0
    trace.asList() == [rootSpan]
    writer == [[child2, child1], [rootSpan]]
    traceCount.get() == 2
  }
}
