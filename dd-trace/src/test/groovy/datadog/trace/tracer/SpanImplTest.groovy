package datadog.trace.tracer

import datadog.trace.api.DDTags
import spock.lang.Specification

class SpanImplTest extends Specification {

  private static final String SERVICE_NAME = "service.name"
  private static final String PARENT_TRACE_ID = "trace id"
  private static final String PARENT_SPAN_ID = "span id"
  private static final long DURATION = 321

  def interceptors = [Mock(name: "interceptor-1", Interceptor), Mock(name: "interceptor-2", Interceptor)]
  def tracer = Mock(Tracer) {
    getDefaultServiceName() >> SERVICE_NAME
    getInterceptors() >> interceptors
  }
  def parentContext = Mock(SpanContextImpl) {
    getTraceId() >> PARENT_TRACE_ID
    getSpanId() >> PARENT_SPAN_ID
  }
  def startTimestamp = Mock(Timestamp) {
    getDuration() >> DURATION
    getDuration(_) >> { args -> args[0] + DURATION }
  }
  def trace = Mock(TraceImpl) {
    getTracer() >> tracer
  }

  def "test setters and default values"() {
    when: "create span"
    def span = new SpanImpl(trace, parentContext, startTimestamp)

    then: "span got created"
    span.getTrace() == trace
    span.getStartTimestamp() == startTimestamp
    span.getDuration() == null
    span.getContext().getTraceId() == PARENT_TRACE_ID
    span.getContext().getParentId() == PARENT_SPAN_ID
    span.getContext().getSpanId() ==~ /\d+/
    span.getService() == SERVICE_NAME
    span.getResource() == null
    span.getType() == null
    span.getName() == null
    !span.isErrored()

    when: "span settings changes"
    span.setService("new.service.name")
    span.setResource("resource")
    span.setType("type")
    span.setName("name")
    span.setErrored(true)

    then: "span fields get updated"
    span.getService() == "new.service.name"
    span.getResource() == "resource"
    span.getType() == "type"
    span.getName() == "name"
    span.isErrored()
  }

  def "test setter #setter on finished span"() {
    setup: "create span"
    def span = new SpanImpl(trace, parentContext, startTimestamp)
    span.finish()

    when: "call setter on finished span"
    span."$setter"(newValue)

    then: "error reported"
    1 * tracer.reportError(_, {
      it[0] == fieldName
      it[1] == span
    })
    and: "value unchanged"
    span."$getter"() == oldValue

    where:
    fieldName  | setter        | getter        | newValue       | oldValue
    "service"  | "setService"  | "getService"  | "new service"  | SERVICE_NAME
    "resource" | "setResource" | "getResource" | "new resource" | null
    "type"     | "setType"     | "getType"     | "new type"     | null
    "name"     | "setName"     | "getName"     | "new name"     | null
    "errored"  | "setErrored"  | "isErrored"   | true           | false
  }

  def "test meta set and remove for #key"() {
    when:
    def span = new SpanImpl(trace, parentContext, startTimestamp)

    then:
    span.getMeta(key) == null

    when:
    span.setMeta(key, value)

    then:
    span.getMeta(key) == value

    when:
    span.setMeta(key, value.class.cast(null))

    then:
    span.getMeta(key) == null

    where:
    key           | value
    "string.key"  | "string"
    "boolean.key" | true
    "number.key"  | 123
  }

  def "test meta setter on finished span for #key"() {
    setup: "create span"
    def span = new SpanImpl(trace, parentContext, startTimestamp)
    span.finish()

    when: "call setter on finished span"
    span.setMeta(key, value)

    then: "error reported"
    1 * tracer.reportError(_, {
      it[0] == "meta value " + key
      it[1] == span
    })
    and: "value unchanged"
    span.getMeta(key) == null

    where:
    key           | value
    "string.key"  | "string"
    "boolean.key" | true
    "number.key"  | 123
  }

  def "test attachThrowable"() {
    setup:
    def exception = new RuntimeException("test message")
    when:
    def span = new SpanImpl(trace, parentContext, startTimestamp)

    then:
    !span.isErrored()
    span.getMeta(DDTags.ERROR_MSG) == null
    span.getMeta(DDTags.ERROR_TYPE) == null
    span.getMeta(DDTags.ERROR_STACK) == null

    when:
    span.attachThrowable(exception)

    then:
    span.isErrored()
    span.getMeta(DDTags.ERROR_MSG) == "test message"
    span.getMeta(DDTags.ERROR_TYPE) == RuntimeException.getName()
    span.getMeta(DDTags.ERROR_STACK) != null
  }

  def "test attachThrowable on finished span"() {
    setup: "create span"
    def exception = new RuntimeException("test message")
    def span = new SpanImpl(trace, parentContext, startTimestamp)
    span.finish()

    when: "attach throwable"
    span.attachThrowable(exception)

    then: "error reported"
    1 * tracer.reportError(_, {
      it[0] == "throwable"
      it[1] == span
    })
    and: "span unchanged"
    !span.isErrored()
    span.getMeta(DDTags.ERROR_MSG) == null
    span.getMeta(DDTags.ERROR_TYPE) == null
    span.getMeta(DDTags.ERROR_STACK) == null
  }

  def "test no parent"() {
    when:
    def span = new SpanImpl(trace, null, startTimestamp)

    then:
    span.getContext().getTraceId() ==~ /\d+/
    span.getContext().getParentId() == SpanContextImpl.ZERO
    span.getContext().getSpanId() ==~ /\d+/
  }

  def "test lifecycle '#name'"() {
    when: "create span"
    def span = new SpanImpl(trace, parentContext, startTimestamp)

    then: "interceptors called"
    interceptors.each({ interceptor ->
      then:
      1 * interceptor.afterSpanStarted({
        // Apparently invocation verification has to know expected value before 'when' section
        // To work around this we just check parent span id
        it.getContext().getParentId() == parentContext.getSpanId()
      })
      0 * interceptor._
    })
    then:
    !span.isFinished()

    when: "finish/finalize span"
    span."$method"(*methodArgs)

    then: "warning is reported"
    _ * trace.getTracer() >> tracer
    if (finalizeErrorReported) {
      1 * tracer.reportWarning(_, span)
    }
    then: "interceptors called"
    interceptors.reverseEach({ interceptor ->
      then:
      1 * interceptor.beforeSpanFinished({
        it == span
        it.getDuration() == null // Make sure duration is not set yet
      })
      0 * interceptor._
    })
    then: "trace is informed that span is closed"
    1 * trace.finishSpan({
      it == span
      it.getDuration() == expectedDuration
    }, finalizeErrorReported)
    0 * trace._
    span.isFinished()

    when: "try to finish span again"
    span.finish(*secondFinishCallArgs)

    then: "interceptors are not called"
    interceptors.each({ interceptor ->
      0 * interceptor._
    })
    and: "trace is not informed"
    0 * trace.finishSpan(_, _)
    and: "error is reported"
    1 * tracer.reportError(_, span)

    where:
    name                   | method     | methodArgs | expectedDuration | finalizeErrorReported | secondFinishCallArgs
    "happy"                | "finish"   | []         | DURATION         | false                 | []
    "happy"                | "finish"   | []         | DURATION         | false                 | [222]
    "happy with timestamp" | "finish"   | [111]      | DURATION + 111   | false                 | [222]
    "happy with timestamp" | "finish"   | [111]      | DURATION + 111   | false                 | []
    // Note: doing GC tests with mocks is nearly impossible because mocks hold all sort of references
    // We will do 'real' GC test as part of integration testing, this is more of a unit test
    "finalized"            | "finalize" | []         | DURATION         | true                  | []
    "finalized"            | "finalize" | []         | DURATION         | true                  | [222]
  }

  def "finished span GCed without errors"() {
    setup: "create span"
    def span = new SpanImpl(trace, parentContext, startTimestamp)

    when: "finish span"
    span.finish()

    then:
    span.isFinished()
    0 * tracer.reportError(_, *_)

    when: "finalize span"
    span.finalize()

    then:
    0 * tracer.reportError(_, *_)
  }

  def "finalize catches all exceptions"() {
    setup:
    def span = new SpanImpl(trace, parentContext, startTimestamp)

    when:
    span.finalize()

    then:
    1 * startTimestamp.getDuration() >> { throw new Throwable() }
    noExceptionThrown()
  }

  def "span without timestamp"() {
    when:
    new SpanImpl(trace, parentContext, null)

    then:
    thrown TraceException
  }
}
