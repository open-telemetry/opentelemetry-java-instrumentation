package datadog.trace.tracer

import datadog.trace.api.Config
import datadog.trace.tracer.sampling.AllSampler
import datadog.trace.tracer.sampling.Sampler
import datadog.trace.tracer.writer.AgentWriter
import datadog.trace.tracer.writer.SampleRateByService
import datadog.trace.tracer.writer.Writer
import datadog.trace.util.gc.GCUtils
import spock.lang.Shared
import spock.lang.Specification

import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class TracerTest extends Specification {

  @Shared
  def config = Config.get()

  def testWriter = new TestWriter()

  // TODO: add more tests for different config options and interceptors
  def "test getters"() {
    when:
    def tracer = Tracer.builder().config(config).build()

    then:
    tracer.getWriter() instanceof AgentWriter
    tracer.getSampler() instanceof AllSampler
    tracer.getInterceptors() == []
  }

  def "close closes writer"() {
    setup:
    def writer = Mock(Writer)
    def tracer = Tracer.builder().writer(writer).build()

    when:
    tracer.close()

    then: "closed writer"
    1 * writer.close()
    0 * _  // don't allow any other interaction
  }

  def "finalize closes writer"() {
    setup:
    def writer = Mock(Writer)
    def tracer = Tracer.builder().writer(writer).build()

    when:
    tracer.finalize()

    then: "closed writer"
    1 * writer.close()
    0 * _  // don't allow any other interaction

    when:
    tracer.finalize()

    then: "thrown error swallowed"
    1 * writer.close() >> { throw new Exception("test error") }
    0 * _  // don't allow any other interaction
  }

  def "test create current timestamp"() {
    setup:
    def tracer = Tracer.builder().config(config).build()

    when:
    def timestamp = tracer.createCurrentTimestamp()

    then:
    timestamp.getDuration() <= TimeUnit.MINUTES.toNanos(1) // Assume test takes less than a minute to run
  }

  def "test trace happy path"() {
    setup:
    def tracer = Tracer.builder().config(config).writer(testWriter).build()

    when:
    def rootSpan = tracer.buildTrace(null)
    def continuation = rootSpan.getTrace().createContinuation(rootSpan)
    def span = rootSpan.getTrace().createSpan(rootSpan.getContext(), tracer.createCurrentTimestamp())

    then:
    rootSpan.getService() == config.getServiceName()

    when:
    rootSpan.finish()
    continuation.close()

    then:
    testWriter.traces == []

    when:
    span.finish()

    then:
    testWriter.traces == [rootSpan.getContext().getTraceId()]
    testWriter.validity == [true]
    rootSpan.getTrace().getSpans() == [rootSpan, span]
    testWriter.traceCount.get() == 1
  }

  def "sampler called on span completion"() {
    setup:
    def sampler = Mock(Sampler)
    def tracer = Tracer.builder().sampler(sampler).build()

    when:
    tracer.buildTrace(null).finish()

    then: "closed writer"
    1 * sampler.sample(_ as Trace)
    0 * _  // don't allow any other interaction
  }

  def "interceptor called on span events"() {
    setup:
    def interceptor = Mock(Interceptor)
    def tracer = Tracer.builder().interceptors([interceptor]).build()

    when:
    tracer.buildTrace(null).finish()

    then: "closed writer"
    1 * interceptor.afterSpanStarted(_ as Span)
    1 * interceptor.beforeSpanFinished(_ as Span)
    1 * interceptor.beforeTraceWritten(_ as Trace)
    0 * _  // don't allow any other interaction
  }

  def "test inject"() {
    //TODO implement this test properly
    setup:
    def context = Mock(SpanContext)
    def tracer = Tracer.builder().config(config).writer(testWriter).build()

    when:
    tracer.inject(context, null, null)

    then:
    noExceptionThrown()
  }

  def "test extract"() {
    //TODO implement this test properly
    setup:
    def tracer = Tracer.builder().config(config).writer(testWriter).build()

    when:
    def context = tracer.extract(null, null)

    then:
    context == null
  }

  def testReportError() {
    //TODO implement this test properly
    setup:
    def tracer = Tracer.builder().config(config).writer(testWriter).build()

    when:
    tracer.reportError("message %s", 123)

    then:
    thrown TraceException
  }

  def "test trace that had a GCed span"() {
    setup:
    def tracer = Tracer.builder().config(config).writer(testWriter).build()

    when: "trace and spans get created"
    def rootSpan = tracer.buildTrace(null)
    def traceId = rootSpan.getContext().getTraceId()
    def span = rootSpan.getTrace().createSpan(rootSpan.getContext(), tracer.createCurrentTimestamp())
    rootSpan.finish()

    then: "nothing is written yet"
    testWriter.traces == []

    when: "remove all references to traces and spans and wait for GC"
    span = null
    def traceRef = new WeakReference<>(rootSpan.getTrace())
    rootSpan = null
    GCUtils.awaitGC(traceRef)

    then: "invalid trace is written"
    testWriter.waitForTraces(1)
    testWriter.traces == [traceId]
    testWriter.validity == [false]
    testWriter.traceCount.get() == 1
  }

  def "test trace that had a GCed continuation"() {
    setup:
    def tracer = Tracer.builder().config(config).writer(testWriter).build()

    when: "trace and spans get created"
    def rootSpan = tracer.buildTrace(null)
    def traceId = rootSpan.getContext().getTraceId()
    def continuation = rootSpan.getTrace().createContinuation(rootSpan)
    rootSpan.finish()

    then: "nothing is written yet"
    testWriter.traces == []

    when: "remove all references to traces and spans and wait for GC"
    continuation = null
    def traceRef = new WeakReference<>(rootSpan.getTrace())
    rootSpan = null
    GCUtils.awaitGC(traceRef)

    then: "invalid trace is written"
    testWriter.waitForTraces(1)
    testWriter.traces == [traceId]
    testWriter.validity == [false]
    testWriter.traceCount.get() == 1
  }

  /**
   * We cannot use mocks for testing of things related to GC because mocks capture arguments with hardlinks.
   * For the same reason this test writer cannot capture complete references to traces in this writer. Instead
   * we capture 'strategic' values. Other values have been tested in 'lower level' tests.
   */
  static class TestWriter implements Writer {

    def traces = new ArrayList<String>()
    def validity = new ArrayList<Boolean>()
    def traceCount = new AtomicInteger()

    @Override
    synchronized void write(Trace trace) {
      traces.add(trace.getRootSpan().getContext().getTraceId())
      validity.add(trace.isValid())
    }

    @Override
    void incrementTraceCount() {
      traceCount.incrementAndGet()
    }

    @Override
    SampleRateByService getSampleRateByService() {
      return null // Doesn't matter for now
    }

    @Override
    void start() {
      //nothing to do for now
    }

    @Override
    void close() {
      //nothing to do for now
    }

    /**
     * JVM gives very little guarantees for when finalizers are run. {@link WeakReference} documentation
     * says that weak reference is set to null first, and then objects are marked as finalizable. Then
     * finalization happens asynchronously in separate thread.
     * This means that currently we do not have a good way of knowing that all traces have been closed/GCed right
     * now and only thing we can do is to bluntly wait.
     * In the future we have plans to implement limiting of number of inflight traces - this might provide us with
     * better way.
     * @param numberOfTraces number of traces to wait for.
     */
    void waitForTraces(int numberOfTraces) {
      while (true) {
        synchronized (this) {
          if (traces.size() >= numberOfTraces && validity.size() >= numberOfTraces) {
            return
          }
        }
        Thread.sleep(500)
      }
    }
  }
}
