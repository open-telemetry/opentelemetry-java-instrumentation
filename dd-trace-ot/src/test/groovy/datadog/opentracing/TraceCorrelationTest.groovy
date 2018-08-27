package datadog.opentracing

import datadog.trace.common.writer.ListWriter
import spock.lang.Shared
import spock.lang.Specification

class TraceCorrelationTest extends Specification {

  static final WRITER = new ListWriter()

  @Shared
  DDTracer tracer = new DDTracer(WRITER)

  def scope = tracer.buildSpan("test").startActive(true)

  def cleanup() {
    scope.close()
  }

  def "get trace id without trace"() {
    setup:
    scope.close()

    expect:
    "0" == tracer.getTraceId()
  }

  def "get trace id with trace"() {
    expect:
    ((DDSpan) scope.span()).traceId == tracer.getTraceId()
  }

  def "get span id without span"() {
    setup:
    scope.close()

    expect:
    "0" == tracer.getSpanId()
  }

  def "get span id with trace"() {
    expect:
    ((DDSpan) scope.span()).spanId == tracer.getSpanId()
  }
}
