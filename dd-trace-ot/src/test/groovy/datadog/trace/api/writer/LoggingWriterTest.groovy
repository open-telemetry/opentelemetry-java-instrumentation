package datadog.trace.api.writer


import datadog.opentracing.DDTracer
import datadog.opentracing.SpanFactory
import datadog.trace.common.writer.LoggingWriter
import datadog.trace.util.test.DDSpecification
import spock.lang.Subject

class LoggingWriterTest extends DDSpecification {
  @Subject
  def writer = new LoggingWriter()

  def tracer = Mock(DDTracer)
  def sampleTrace = [SpanFactory.newSpanOf(tracer), SpanFactory.newSpanOf(tracer)]

  def "test toString"() {
    expect:
    writer.toString(sampleTrace).startsWith('[{"service":"fakeService","name":"fakeOperation","resource":"fakeResource","trace_id":1,"span_id":1,"parent_id":0,"start":1000,"duration":0,"type":"fakeType","error":0,"metrics":{},"meta":{')
  }
}
