package datadog.opentracing.propagation

import datadog.opentracing.DDSpanContext
import datadog.opentracing.DDTracer
import datadog.opentracing.PendingTrace
import datadog.trace.common.writer.ListWriter
import datadog.trace.util.test.DDSpecification
import io.opentracing.propagation.TextMapInjectAdapter

class HttpInjectorTest extends DDSpecification {

  def "inject http headers"() {
    HttpCodec.Injector injector = HttpCodec.createInjector()

    def traceId = 1G
    def spanId = 2G

    def writer = new ListWriter()
    def tracer = new DDTracer(writer)
    final DDSpanContext mockedContext =
      new DDSpanContext(
        traceId,
        spanId,
        0G,
        "fakeOperation",
        false,
        null,
        new PendingTrace(tracer, 1G),
        tracer)

    final Map<String, String> carrier = Mock()

    when:
    injector.inject(mockedContext, new TextMapInjectAdapter(carrier))

    then:
    1 * carrier.put(DatadogHttpCodec.TRACE_ID_KEY, traceId.toString())
    1 * carrier.put(DatadogHttpCodec.SPAN_ID_KEY, spanId.toString())
    0 * _
  }
}
