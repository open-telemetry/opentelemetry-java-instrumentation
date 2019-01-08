package datadog.trace.tracer

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import datadog.trace.tracer.sampling.Sampler
import datadog.trace.tracer.writer.Writer
import spock.lang.Specification

class TraceImplTest extends Specification {

  private static final String SERVICE_NAME = "service.name"
  private static final String PARENT_TRACE_ID = "trace id"
  private static final String PARENT_SPAN_ID = "span id"

  def interceptors = [
    Mock(name: "interceptor-1", Interceptor) {
      beforeTraceWritten(_) >> { args -> args[0] }
    },
    Mock(name: "interceptor-2", Interceptor) {
      beforeTraceWritten(_) >> { args -> args[0] }
    }
  ]
  def writer = Mock(Writer)
  def sampler = Mock(Sampler) {
    sample(_) >> true
  }
  def tracer = Mock(Tracer) {
    getDefaultServiceName() >> SERVICE_NAME
    getInterceptors() >> interceptors
    getWriter() >> writer
    getSampler() >> sampler
  }
  def parentContext = Mock(SpanContextImpl) {
    getTraceId() >> PARENT_TRACE_ID
    getSpanId() >> PARENT_SPAN_ID
  }
  def startTimestamp = Mock(Timestamp)

  ObjectMapper objectMapper = new ObjectMapper()

  def "test getters"() {
    when:
    def trace = new TraceImpl(tracer, parentContext, startTimestamp)

    then:
    trace.getTracer() == tracer
    trace.getRootSpan().getTrace() == trace
    trace.getRootSpan().getStartTimestamp() == startTimestamp
    trace.getRootSpan().getContext().getTraceId() == PARENT_TRACE_ID
    trace.getRootSpan().getContext().getParentId() == PARENT_SPAN_ID
    trace.isValid()
  }

  def "test getSpans on unfinished spans"() {
    setup:
    def trace = new TraceImpl(tracer, parentContext, startTimestamp)

    when:
    trace.getSpans()

    then:
    1 * tracer.reportError(_, trace)
  }

  def "test timestamp creation"() {
    setup:
    def newTimestamp = Mock(Timestamp)
    def clock = Mock(Clock) {
      createCurrentTimestamp() >> newTimestamp
    }
    startTimestamp.getClock() >> clock
    def trace = new TraceImpl(tracer, parentContext, startTimestamp)

    when:
    def createdTimestamp = trace.createCurrentTimestamp()

    then:
    createdTimestamp == newTimestamp
  }

  def "finish root span"() {
    setup:
    def trace = new TraceImpl(tracer, parentContext, startTimestamp)

    when: "root span is finished"
    trace.getRootSpan().finish()

    then: "trace gets counted"
    1 * writer.incrementTraceCount()
    then: "interceptors get called"
    interceptors.reverseEach({ interceptor ->
      then:
      1 * interceptor.beforeTraceWritten(trace) >> trace
    })
    then: "trace gets sampled"
    1 * sampler.sample(trace) >> { true }
    then: "trace gets written"
    1 * writer.write(trace)
    trace.isValid()
    trace.getSpans() == [trace.getRootSpan()]

    when: "root span is finalized"
    trace.getRootSpan().finalize()

    then: "nothing happens"
    0 * writer.incrementTraceCount()
    interceptors.reverseEach({ interceptor ->
      0 * interceptor.beforeTraceWritten(_)
    })
    0 * sampler.sample(_)
    0 * writer.write(_)
    0 * tracer.reportError(_, *_)
  }

  def "GC root span"() {
    setup:
    def trace = new TraceImpl(tracer, parentContext, startTimestamp)

    when: "root span is finalized"
    trace.getRootSpan().finalize()

    then: "trace gets counted"
    1 * writer.incrementTraceCount()
    then: "interceptors get called"
    interceptors.reverseEach({ interceptor ->
      then:
      1 * interceptor.beforeTraceWritten(trace) >> trace
    })
    then: "trace gets sampled"
    1 * sampler.sample(trace) >> { true }
    then: "trace gets written"
    1 * writer.write(trace)
    !trace.isValid()
  }

  def "finish root span dropped by interceptor"() {
    setup:
    def trace = new TraceImpl(tracer, parentContext, startTimestamp)

    when:
    trace.getRootSpan().finish()

    then: "trace gets counted"
    1 * writer.incrementTraceCount()
    then:
    1 * interceptors[1].beforeTraceWritten(trace) >> null
    0 * interceptors[0].beforeTraceWritten(_)
    0 * sampler.sample(_)
    0 * writer.write(_)
  }

  def "finish root span replaced by interceptor"() {
    setup:
    def trace = new TraceImpl(tracer, parentContext, startTimestamp)
    def replacementTrace = new TraceImpl(tracer, parentContext, startTimestamp)

    when:
    trace.getRootSpan().finish()

    then: "trace gets counted"
    1 * writer.incrementTraceCount()
    then:
    1 * interceptors[1].beforeTraceWritten(trace) >> replacementTrace
    then:
    1 * interceptors[0].beforeTraceWritten(replacementTrace) >> replacementTrace
    then:
    1 * sampler.sample(replacementTrace) >> true
    then:
    1 * writer.write(replacementTrace)
  }

  def "finish root span dropped by sampler"() {
    setup:
    def trace = new TraceImpl(tracer, parentContext, startTimestamp)

    when:
    trace.getRootSpan().finish()

    then: "trace gets counted"
    1 * writer.incrementTraceCount()
    then:
    1 * sampler.sample(trace) >> false
    0 * writer.write(_)
  }

  def "finish root span and then finish it again by error"() {
    setup:
    def trace = new TraceImpl(tracer, parentContext, startTimestamp)

    when: "root span is finished"
    trace.getRootSpan().finish()

    then: "trace gets counted"
    1 * writer.incrementTraceCount()
    then: "interceptors get called"
    interceptors.reverseEach({ interceptor ->
      then:
      1 * interceptor.beforeTraceWritten(trace) >> trace
    })
    then: "trace gets sampled"
    1 * sampler.sample(trace) >> { true }
    then: "trace gets written"
    1 * writer.write(trace)

    when: "root span is finalized"
    trace.finishSpan(trace.getRootSpan(), false)

    then: "error is reported"
    interceptors.reverseEach({ interceptor ->
      0 * interceptor.beforeTraceWritten(_)
    })
    0 * sampler.sample(_)
    0 * writer.incrementTraceCount()
    0 * writer.write(_)
    1 * tracer.reportError(_, "finish span", trace)
  }

  def "create and finish new span"() {
    setup:
    def newSpanTimestamp = Mock(Timestamp)
    def trace = new TraceImpl(tracer, parentContext, startTimestamp)

    when: "new span is created"
    def span = trace.createSpan(trace.getRootSpan().getContext(), newSpanTimestamp)

    then:
    span.getTrace() == trace
    span.getStartTimestamp() == newSpanTimestamp
    span.getContext().getTraceId() == PARENT_TRACE_ID
    span.getContext().getParentId() == trace.getRootSpan().getContext().getSpanId()

    when: "root span is finished"
    trace.getRootSpan().finish()

    then: "nothing gets written"
    0 * writer.incrementTraceCount()
    0 * writer.write(_)

    when: "new span is finished"
    span.finish()

    then: "trace gets written"
    1 * writer.incrementTraceCount()
    1 * writer.write(trace)
    trace.isValid()
    trace.getSpans() == [trace.getRootSpan(), span]
  }

  def "create and finish new span with default timestamp"() {
    setup:
    def newSpanTimestamp = Mock(Timestamp)
    def clock = Mock(Clock) {
      createCurrentTimestamp() >> newSpanTimestamp
    }
    startTimestamp.getClock() >> clock
    def trace = new TraceImpl(tracer, parentContext, startTimestamp)

    when: "new span is created"
    def span = trace.createSpan(trace.getRootSpan().getContext())

    then:
    span.getTrace() == trace
    span.getStartTimestamp() == newSpanTimestamp
    span.getContext().getTraceId() == PARENT_TRACE_ID
    span.getContext().getParentId() == trace.getRootSpan().getContext().getSpanId()

    when: "root span is finished"
    trace.getRootSpan().finish()

    then: "nothing gets written"
    0 * writer.incrementTraceCount()
    0 * writer.write(_)

    when: "new span is finished"
    span.finish()

    then: "trace gets written"
    1 * writer.incrementTraceCount()
    1 * writer.write(trace)
    trace.isValid()
    trace.getSpans() == [trace.getRootSpan(), span]
  }

  def "create span on finished trace"() {
    setup:
    def trace = new TraceImpl(tracer, parentContext, startTimestamp)

    when: "root span is finished"
    trace.getRootSpan().finish()

    then: "trace is finished"
    trace.getSpans() != []

    when: "new span is created"
    trace.createSpan(trace.getRootSpan().getContext(), startTimestamp)

    then: "error is reported"
    1 * tracer.reportError(_, "create span", trace)
  }

  def "create span and finish it twice"() {
    setup:
    def trace = new TraceImpl(tracer, parentContext, startTimestamp)
    def span = trace.createSpan(trace.getRootSpan().getContext(), startTimestamp)

    when: "new span is created"
    span.finish()
    trace.finishSpan(span, true)

    then: "error is reported"
    1 * tracer.reportError(_, span, trace)
  }

  def "create span with null parent context"() {
    setup:
    def trace = new TraceImpl(tracer, parentContext, startTimestamp)

    when: "new span with null parent context is created"
    trace.createSpan(null, startTimestamp)

    then: "error is reported"
    thrown TraceException
  }

  def "create span with parent context from different trace"() {
    setup:
    def trace = new TraceImpl(tracer, parentContext, startTimestamp)
    def anotherParentContext = Mock(SpanContextImpl) {
      getTraceId() >> "different trace"
      getSpanId() >> PARENT_SPAN_ID
    }

    when: "new span with null parent context is created"
    trace.createSpan(anotherParentContext, startTimestamp)

    then: "error is reported"
    thrown TraceException
  }

  def "create and close new continuation"() {
    setup:
    def trace = new TraceImpl(tracer, parentContext, startTimestamp)

    when: "new continuation is created"
    def continuation = trace.createContinuation(trace.getRootSpan())

    then:
    continuation.getSpan() == trace.getRootSpan()

    when: "root span is finished"
    trace.getRootSpan().finish()

    then: "nothing gets written"
    0 * writer.incrementTraceCount()
    0 * writer.write(_)

    when: "new continuation is closed"
    continuation.close()

    then: "trace gets written"
    1 * writer.incrementTraceCount()
    1 * writer.write(trace)
    trace.isValid()
    trace.getSpans() == [trace.getRootSpan()]
  }

  def "GC continuation"() {
    setup:
    def trace = new TraceImpl(tracer, parentContext, startTimestamp)
    def continuation = trace.createContinuation(trace.getRootSpan())
    trace.getRootSpan().finish()

    when: "continuation finalized"
    continuation.finalize()

    then: "trace gets counted"
    1 * writer.incrementTraceCount()
    then: "interceptors get called"
    interceptors.reverseEach({ interceptor ->
      then:
      1 * interceptor.beforeTraceWritten(trace) >> trace
    })
    then: "trace gets sampled"
    1 * sampler.sample(trace) >> { true }
    then: "trace gets written"
    1 * writer.write(trace)
    !trace.isValid()
  }

  def "create and close new continuation, then close it again"() {
    setup:
    def trace = new TraceImpl(tracer, parentContext, startTimestamp)
    def continuation = trace.createContinuation(trace.getRootSpan())
    continuation.close()

    when: "continuation is closed again"
    trace.closeContinuation(continuation, true)

    then: "error is reported"
    1 * tracer.reportError(_, continuation, trace)
  }

  def "create and close new continuation, then close it in finished trace"() {
    setup:
    def trace = new TraceImpl(tracer, parentContext, startTimestamp)
    def continuation = trace.createContinuation(trace.getRootSpan())
    continuation.close()
    trace.getRootSpan().finish()

    when: "continuation is closed again"
    trace.closeContinuation(continuation, true)

    then: "error is reported"
    1 * tracer.reportError(_, "close continuation", trace)
  }

  def "create continuation on finished trace"() {
    setup:
    def trace = new TraceImpl(tracer, parentContext, startTimestamp)

    when: "root span is finished"
    trace.getRootSpan().finish()

    then: "trace is finished"
    trace.getSpans() != []

    when: "new continuation is created"
    trace.createContinuation(trace.getRootSpan())

    then: "error is reported"
    1 * tracer.reportError(_, "create continuation", trace)
  }

  def "create continuation with null parent span"() {
    setup:
    def trace = new TraceImpl(tracer, parentContext, startTimestamp)

    when: "new continuation with null parent span is created"
    trace.createContinuation(null)

    then: "error is reported"
    thrown TraceException
  }

  def "create continuation with parent span from another trace"() {
    setup:
    def trace = new TraceImpl(tracer, parentContext, startTimestamp)
    def anotherParentContext = Mock(SpanContextImpl) {
      getTraceId() >> "different trace"
      getSpanId() >> PARENT_SPAN_ID
    }
    def anotherParentSpan = Mock(SpanImpl) {
      getContext() >> anotherParentContext
    }

    when: "new continuation from span from another trace is created"
    trace.createContinuation(anotherParentSpan)

    then: "error is reported"
    thrown TraceException
  }

  def "test JSON rendering"() {
    setup: "create trace"
    def clock = new Clock(tracer)
    def parentContext = new SpanContextImpl("123", "456", "789")
    def trace = new TraceImpl(tracer, parentContext, clock.createCurrentTimestamp())
    trace.getRootSpan().setResource("test resource")
    trace.getRootSpan().setType("test type")
    trace.getRootSpan().setName("test name")
    trace.getRootSpan().setMeta("number.key", 123)
    trace.getRootSpan().setMeta("string.key", "meta string")
    trace.getRootSpan().setMeta("boolean.key", true)

    def childSpan = trace.createSpan(trace.getRootSpan().getContext())
    childSpan.setResource("child span test resource")
    childSpan.setType("child span test type")
    childSpan.setName("child span test name")
    childSpan.setMeta("child.span.number.key", 123)
    childSpan.setMeta("child.span.string.key", "meta string")
    childSpan.setMeta("child.span.boolean.key", true)
    childSpan.finish()

    trace.getRootSpan().finish()

    when: "convert to JSON"
    def string = objectMapper.writeValueAsString(trace)
    def parsedTrace = objectMapper.readValue(string, new TypeReference<List<JsonSpan>>() {})

    then:
    parsedTrace == [new JsonSpan(childSpan), new JsonSpan(trace.getRootSpan())]
  }
}
