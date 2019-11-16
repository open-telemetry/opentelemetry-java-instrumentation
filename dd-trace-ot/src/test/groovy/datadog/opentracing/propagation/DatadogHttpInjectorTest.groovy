package datadog.opentracing.propagation

import datadog.opentracing.DDSpanContext
import datadog.opentracing.DDTracer
import datadog.opentracing.PendingTrace
import datadog.trace.common.writer.ListWriter
import datadog.trace.util.test.DDSpecification
import io.opentracing.propagation.TextMapInjectAdapter

import static datadog.opentracing.DDTracer.TRACE_ID_MAX
import static datadog.opentracing.propagation.DatadogHttpCodec.SPAN_ID_KEY
import static datadog.opentracing.propagation.DatadogHttpCodec.TRACE_ID_KEY

class DatadogHttpInjectorTest extends DDSpecification {

  HttpCodec.Injector injector = new DatadogHttpCodec.Injector()

  def "inject http headers"() {
    setup:
    def writer = new ListWriter()
    def tracer = new DDTracer(writer)
    final DDSpanContext mockedContext =
      new DDSpanContext(
        traceId,
        spanId,
        0G,
        "fakeOperation",
        "fakeResource",
        false,
        "fakeType",
        null,
        new PendingTrace(tracer, 1G),
        tracer)

    final Map<String, String> carrier = Mock()

    when:
    injector.inject(mockedContext, new TextMapInjectAdapter(carrier))

    then:
    1 * carrier.put(TRACE_ID_KEY, traceId.toString())
    1 * carrier.put(SPAN_ID_KEY, spanId.toString())
    0 * _

    where:
    traceId          | spanId
    1G               | 2G
    TRACE_ID_MAX     | TRACE_ID_MAX - 1
    TRACE_ID_MAX - 1 | TRACE_ID_MAX
  }
}
