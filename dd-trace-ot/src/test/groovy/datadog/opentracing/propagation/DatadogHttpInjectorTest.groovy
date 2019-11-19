package datadog.opentracing.propagation

import datadog.opentracing.DDSpanContext
import datadog.opentracing.DDTracer
import datadog.opentracing.PendingTrace
import datadog.trace.common.writer.ListWriter
import datadog.trace.util.test.DDSpecification
import io.opentracing.propagation.TextMapInjectAdapter

import static datadog.opentracing.DDTracer.TRACE_ID_MAX
import static datadog.opentracing.propagation.DatadogHttpCodec.ORIGIN_KEY
import static datadog.opentracing.propagation.DatadogHttpCodec.OT_BAGGAGE_PREFIX
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
        "fakeService",
        "fakeOperation",
        "fakeResource",
        origin,
        new HashMap<String, String>() {
          {
            put("k1", "v1")
            put("k2", "v2")
          }
        },
        false,
        "fakeType",
        null,
        new PendingTrace(tracer, 1G, [:]),
        tracer)

    final Map<String, String> carrier = Mock()

    when:
    injector.inject(mockedContext, new TextMapInjectAdapter(carrier))

    then:
    1 * carrier.put(TRACE_ID_KEY, traceId.toString())
    1 * carrier.put(SPAN_ID_KEY, spanId.toString())
    1 * carrier.put(OT_BAGGAGE_PREFIX + "k1", "v1")
    1 * carrier.put(OT_BAGGAGE_PREFIX + "k2", "v2")
    if (origin) {
      1 * carrier.put(ORIGIN_KEY, origin)
    }
    0 * _

    where:
    traceId          | spanId           | origin
    1G               | 2G               | null
    1G               | 2G               | "saipan"
    TRACE_ID_MAX     | TRACE_ID_MAX - 1 | "saipan"
    TRACE_ID_MAX - 1 | TRACE_ID_MAX     | null
  }
}
