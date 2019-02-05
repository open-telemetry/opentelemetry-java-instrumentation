package datadog.opentracing.propagation

import datadog.opentracing.DDSpanContext
import datadog.opentracing.DDTracer
import datadog.opentracing.PendingTrace
import datadog.trace.api.sampling.PrioritySampling
import datadog.trace.common.writer.ListWriter
import io.opentracing.propagation.TextMapInjectAdapter
import spock.lang.Specification

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
        "1",
        "2",
        "0",
        "fakeService",
        "fakeOperation",
        "fakeResource",
        samplingPriority,
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

    final Map<String, String> carrier = new HashMap<>()

    injector.inject(mockedContext, new TextMapInjectAdapter(carrier))

    expect:
    carrier.get(TRACE_ID_KEY) == "1"
    carrier.get(SPAN_ID_KEY) == "2"
    carrier.get(SAMPLING_PRIORITY_KEY) == (samplingPriority == PrioritySampling.UNSET ? null : String.valueOf(samplingPriority))
    carrier.get(OT_BAGGAGE_PREFIX + "k1") == "v1"
    carrier.get(OT_BAGGAGE_PREFIX + "k2") == "v2"

    where:
    samplingPriority              | _
    PrioritySampling.UNSET        | _
    PrioritySampling.SAMPLER_KEEP | _
  }

  def "inject http headers with larger than Java long IDs"() {
    String largeTraceId = "9523372036854775807"
    String largeSpanId = "15815582334751494918"
    String largeParentId = "15815582334751494914"
    setup:
    def writer = new ListWriter()
    def tracer = new DDTracer(writer)
    final DDSpanContext mockedContext =
      new DDSpanContext(
        largeTraceId,
        largeSpanId,
        largeParentId,
        "fakeService",
        "fakeOperation",
        "fakeResource",
        samplingPriority,
        new HashMap<String, String>() {
          {
            put("k1", "v1")
            put("k2", "v2")
          }
        },
        false,
        "fakeType",
        null,
        new PendingTrace(tracer, largeTraceId, [:]),
        tracer)

    final Map<String, String> carrier = new HashMap<>()

    injector.inject(mockedContext, new TextMapInjectAdapter(carrier))

    expect:
    carrier.get(TRACE_ID_KEY) == largeTraceId
    carrier.get(SPAN_ID_KEY) == largeSpanId
    carrier.get(SAMPLING_PRIORITY_KEY) == (samplingPriority == PrioritySampling.UNSET ? null : String.valueOf(samplingPriority))
    carrier.get(OT_BAGGAGE_PREFIX + "k1") == "v1"
    carrier.get(OT_BAGGAGE_PREFIX + "k2") == "v2"

    where:
    samplingPriority              | _
    PrioritySampling.UNSET        | _
    PrioritySampling.SAMPLER_KEEP | _
  }

  def "inject http headers with uint 64 max IDs"() {
    String largeTraceId = "18446744073709551615"
    String largeSpanId = "18446744073709551614"
    String largeParentId = "18446744073709551613"
    setup:
    def writer = new ListWriter()
    def tracer = new DDTracer(writer)
    final DDSpanContext mockedContext =
      new DDSpanContext(
        largeTraceId,
        largeSpanId,
        largeParentId,
        "fakeService",
        "fakeOperation",
        "fakeResource",
        samplingPriority,
        new HashMap<String, String>() {
          {
            put("k1", "v1")
            put("k2", "v2")
          }
        },
        false,
        "fakeType",
        null,
        new PendingTrace(tracer, largeTraceId, [:]),
        tracer)

    final Map<String, String> carrier = new HashMap<>()

    injector.inject(mockedContext, new TextMapInjectAdapter(carrier))

    expect:
    carrier.get(TRACE_ID_KEY) == largeTraceId
    carrier.get(SPAN_ID_KEY) == largeSpanId
    carrier.get(SAMPLING_PRIORITY_KEY) == (samplingPriority == PrioritySampling.UNSET ? null : String.valueOf(samplingPriority))
    carrier.get(OT_BAGGAGE_PREFIX + "k1") == "v1"
    carrier.get(OT_BAGGAGE_PREFIX + "k2") == "v2"

    where:
    samplingPriority              | _
    PrioritySampling.UNSET        | _
    PrioritySampling.SAMPLER_KEEP | _
  }
}
