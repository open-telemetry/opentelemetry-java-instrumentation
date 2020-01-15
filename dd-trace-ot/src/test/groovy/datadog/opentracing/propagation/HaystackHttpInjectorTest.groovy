package datadog.opentracing.propagation

import datadog.opentracing.DDSpanContext
import datadog.opentracing.DDTracer
import datadog.opentracing.PendingTrace
import datadog.trace.api.sampling.PrioritySampling
import datadog.trace.common.writer.ListWriter
import datadog.trace.util.test.DDSpecification
import io.opentracing.propagation.TextMapInjectAdapter

import static datadog.opentracing.DDTracer.TRACE_ID_MAX
import static datadog.opentracing.propagation.HaystackHttpCodec.OT_BAGGAGE_PREFIX
import static datadog.opentracing.propagation.HaystackHttpCodec.SPAN_ID_KEY
import static datadog.opentracing.propagation.HaystackHttpCodec.TRACE_ID_KEY

class HaystackHttpInjectorTest extends DDSpecification {

  HttpCodec.Injector injector = new HaystackHttpCodec.Injector()

  def "inject http headers"() {
    setup:
    def writer = new ListWriter()
    def tracer = DDTracer.builder().writer(writer).build()
    final DDSpanContext mockedContext =
      new DDSpanContext(
        traceId,
        spanId,
        0G,
        "fakeService",
        "fakeOperation",
        "fakeResource",
        samplingPriority,
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


    where:
    traceId          | spanId           | samplingPriority              | origin
    1G               | 2G               | PrioritySampling.SAMPLER_KEEP | null
    1G               | 2G               | PrioritySampling.SAMPLER_KEEP | null
    TRACE_ID_MAX     | TRACE_ID_MAX - 1 | PrioritySampling.SAMPLER_KEEP | null
    TRACE_ID_MAX - 1 | TRACE_ID_MAX     | PrioritySampling.SAMPLER_KEEP | null
  }
}
