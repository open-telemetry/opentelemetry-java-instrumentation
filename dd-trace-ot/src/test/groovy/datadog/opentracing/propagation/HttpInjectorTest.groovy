package datadog.opentracing.propagation

import datadog.opentracing.DDSpanContext
import datadog.opentracing.DDTracer
import datadog.opentracing.PendingTrace
import datadog.trace.api.Config
import datadog.trace.api.sampling.PrioritySampling
import datadog.trace.common.writer.ListWriter
import io.opentracing.propagation.TextMapInjectAdapter
import spock.lang.Specification

import static datadog.trace.api.Config.PropagationStyle.B3
import static datadog.trace.api.Config.PropagationStyle.DATADOG

class HttpInjectorTest extends Specification {

  def "inject http headers"() {
    setup:
    Config config = Mock(Config) {
      getPropagationStylesToInject() >> styles
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
    if (styles.contains(DATADOG)) {
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
    if (styles.contains(B3)) {
      1 * carrier.put(B3HttpCodec.TRACE_ID_KEY, traceId)
      1 * carrier.put(B3HttpCodec.SPAN_ID_KEY, spanId)
      if (samplingPriority != PrioritySampling.UNSET) {
        1 * carrier.put(B3HttpCodec.SAMPLING_PRIORITY_KEY, "1")
      }
    }
    0 * _

    where:
    styles        | samplingPriority              | origin
    [DATADOG, B3] | PrioritySampling.UNSET        | null
    [DATADOG, B3] | PrioritySampling.SAMPLER_KEEP | "saipan"
    [DATADOG]     | PrioritySampling.UNSET        | null
    [DATADOG]     | PrioritySampling.SAMPLER_KEEP | "saipan"
    [B3]          | PrioritySampling.UNSET        | null
    [B3]          | PrioritySampling.SAMPLER_KEEP | "saipan"
    [B3, DATADOG] | PrioritySampling.SAMPLER_KEEP | "saipan"
  }
}
