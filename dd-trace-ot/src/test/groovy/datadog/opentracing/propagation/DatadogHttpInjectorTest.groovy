package datadog.opentracing.propagation

import datadog.opentracing.DDSpanContext
import datadog.opentracing.DDTracer
import datadog.opentracing.PendingTrace
import datadog.trace.api.sampling.PrioritySampling
import datadog.trace.common.writer.ListWriter
import io.opentracing.propagation.TextMapInjectAdapter
import spock.lang.Specification

import static datadog.opentracing.propagation.DatadogHttpCodec.ORIGIN_KEY
import static datadog.opentracing.propagation.DatadogHttpCodec.OT_BAGGAGE_PREFIX
import static datadog.opentracing.propagation.DatadogHttpCodec.SAMPLING_PRIORITY_KEY
import static datadog.opentracing.propagation.DatadogHttpCodec.SPAN_ID_KEY
import static datadog.opentracing.propagation.DatadogHttpCodec.TRACE_ID_KEY

class DatadogHttpInjectorTest extends Specification {

  DatadogHttpCodec.Injector injector = new DatadogHttpCodec.Injector()

  def "inject http headers"() {
    setup:
    def writer = new ListWriter()
    def tracer = new DDTracer(writer)
    final DDSpanContext mockedContext =
      new DDSpanContext(
        traceID,
        spanID,
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
    1 * carrier.put(TRACE_ID_KEY, traceID)
    1 * carrier.put(SPAN_ID_KEY, spanID)
    1 * carrier.put(OT_BAGGAGE_PREFIX + "k1", "v1")
    1 * carrier.put(OT_BAGGAGE_PREFIX + "k2", "v2")
    if (samplingPriority != PrioritySampling.UNSET) {
      1 * carrier.put(SAMPLING_PRIORITY_KEY, "$samplingPriority")
    }
    if (origin) {
      1 * carrier.put(ORIGIN_KEY, origin)
    }
    0 * _

    where:
    traceID                | spanID                 | parentID               | samplingPriority              | origin
    "1"                    | "2"                    | "0"                    | PrioritySampling.UNSET        | null
    "1"                    | "2"                    | "0"                    | PrioritySampling.SAMPLER_KEEP | "saipan"
    // Test with numbers exceeding Long.MAX_VALUE (uint64)
    "9523372036854775807"  | "15815582334751494918" | "15815582334751494914" | PrioritySampling.UNSET        | "saipan"
    "18446744073709551615" | "18446744073709551614" | "18446744073709551613" | PrioritySampling.SAMPLER_KEEP | null
  }
}
