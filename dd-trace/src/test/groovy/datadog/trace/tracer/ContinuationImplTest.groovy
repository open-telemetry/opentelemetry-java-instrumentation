package datadog.trace.tracer


import spock.lang.Specification

class ContinuationImplTest extends Specification {

  def tracer = Mock(Tracer)
  def trace = Mock(TraceImpl) {
    getTracer() >> tracer
  }
  def span = Mock(Span)

  def "test getters"() {
    when:
    def continuation = new ContinuationImpl(trace, span)

    then:
    continuation.getTrace() == trace
    continuation.getSpan() == span
  }

  def "happy lifecycle"() {
    when: "continuation is created"
    def continuation = new ContinuationImpl(trace, span)

    then: "continuation is opened"
    !continuation.isClosed()

    when: "close continuation"
    continuation.close()

    then: "continuation is closed and no errors are reported"
    continuation.isClosed()
    0 * tracer.reportError(*_)
    and: "continuation is reported as closed to a trace"
    1 * trace.closeContinuation(continuation, false)

    when: "continuation is finalized"
    continuation.finalize()

    then: "continuation is still closed and no errors are reported"
    continuation.isClosed()
    0 * tracer.reportError(*_)
    and: "continuation is not reported as closed to a trace again"
    0 * trace.closeContinuation(_, _)
  }

  def "double close"() {
    setup:
    def continuation = new ContinuationImpl(trace, span)

    when: "close continuation"
    continuation.close()

    then: "continuation is closed"
    continuation.isClosed()

    when: "close continuation again"
    continuation.close()

    then: "error is reported"
    1 * tracer.reportError(_, [continuation])
    and: "continuation is not reported as closed to a trace again"
    0 * trace.closeContinuation(_, _)
  }

  def "finalize"() {
    setup:
    def continuation = new ContinuationImpl(trace, span)

    when: "finalize continuation"
    continuation.finalize()

    then: "continuation is closed"
    continuation.isClosed()
    and: "continuation is reported as closed to a trace"
    1 * trace.closeContinuation(continuation, true)

    when: "finalize continuation again"
    continuation.finalize()

    then: "continuation is still closed"
    continuation.isClosed()
    and: "continuation is not reported as closed to a trace again"
    0 * trace.closeContinuation(_, _)
    and: "no error is reported"
    0 * tracer.reportError(_, *_)
  }

  def "finalize catches all exceptions"() {
    setup:
    def continuation = new ContinuationImpl(trace, span)

    when:
    continuation.finalize()

    then:
    1 * trace.closeContinuation(_, _) >> { throw new Throwable() }
    noExceptionThrown()
  }

}
