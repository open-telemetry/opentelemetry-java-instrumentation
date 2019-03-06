package datadog.opentracing.propagation

import datadog.opentracing.DDSpanContext
import datadog.opentracing.DDTracer
import datadog.opentracing.PendingTrace
import datadog.trace.api.Config
import datadog.trace.api.sampling.PrioritySampling
import datadog.trace.common.writer.ListWriter
import io.opentracing.propagation.TextMapInjectAdapter
import spock.lang.Specification

class HttpInjectorTest extends Specification {

  def "inject http headers"() {
    setup:
    Config config = Mock(Config) {
      isInjectDatadogHeaders() >> datadogEnabled
      isInjectB3Headers() >> b3Enabled
    }
    HttpCodec.Injector injector = HttpCodec.createInjector(config)

    def traceId = "1"
    def spanId = "2"

    def writer = new ListWriter()
    def tracer = new DDTracer(writer)
    final DDSpanContext mockedContext =
      new DDSpanContext(
        traceId,
        spanId,
        "0",
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
        new PendingTrace(tracer, "1", [:]),
        tracer)

    final Map<String, String> carrier = Mock()

    when:
    injector.inject(mockedContext, new TextMapInjectAdapter(carrier))

    then:
    if (datadogEnabled) {
      1 * carrier.put(DatadogHttpCodec.TRACE_ID_KEY, traceId)
      1 * carrier.put(DatadogHttpCodec.SPAN_ID_KEY, spanId)
      1 * carrier.put(DatadogHttpCodec.OT_BAGGAGE_PREFIX + "k1", "v1")
      1 * carrier.put(DatadogHttpCodec.OT_BAGGAGE_PREFIX + "k2", "v2")
      if (samplingPriority != PrioritySampling.UNSET) {
        1 * carrier.put(DatadogHttpCodec.SAMPLING_PRIORITY_KEY, "$samplingPriority")
      }
      if (origin) {
        1 * carrier.put(DatadogHttpCodec.ORIGIN_KEY, origin)
      }
    }
    if (b3Enabled) {
      1 * carrier.put(B3HttpCodec.TRACE_ID_KEY, traceId)
      1 * carrier.put(B3HttpCodec.SPAN_ID_KEY, spanId)
      if (samplingPriority != PrioritySampling.UNSET) {
        1 * carrier.put(B3HttpCodec.SAMPLING_PRIORITY_KEY, "1")
      }
    }
    0 * _

    where:
    datadogEnabled | b3Enabled | samplingPriority              | origin
    true           | true      | PrioritySampling.UNSET        | null
    true           | true      | PrioritySampling.SAMPLER_KEEP | "saipan"
    true           | false     | PrioritySampling.UNSET        | null
    true           | false     | PrioritySampling.SAMPLER_KEEP | "saipan"
    false          | true      | PrioritySampling.UNSET        | null
    false          | true      | PrioritySampling.SAMPLER_KEEP | "saipan"
  }
}
