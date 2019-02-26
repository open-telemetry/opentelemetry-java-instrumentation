package datadog.opentracing.propagation

import datadog.trace.api.sampling.PrioritySampling
import io.opentracing.propagation.TextMapExtractAdapter
import spock.lang.Specification

import static datadog.opentracing.propagation.DatadogHttpCodec.BIG_INTEGER_UINT64_MAX
import static datadog.opentracing.propagation.DatadogHttpCodec.OT_BAGGAGE_PREFIX
import static datadog.opentracing.propagation.DatadogHttpCodec.SAMPLING_PRIORITY_KEY
import static datadog.opentracing.propagation.DatadogHttpCodec.SPAN_ID_KEY
import static datadog.opentracing.propagation.DatadogHttpCodec.TRACE_ID_KEY

class DatadogHttpExtractorTest extends Specification {

  DatadogHttpCodec.Extractor extractor = new DatadogHttpCodec.Extractor(["SOME_HEADER": "some-tag"])

  def "extract http headers"() {
    setup:
    final Map<String, String> actual = [
      (TRACE_ID_KEY.toUpperCase())            : "1",
      (SPAN_ID_KEY.toUpperCase())             : "2",
      (OT_BAGGAGE_PREFIX.toUpperCase() + "k1"): "v1",
      (OT_BAGGAGE_PREFIX.toUpperCase() + "k2"): "v2",
      SOME_HEADER                             : "my-interesting-info",
    ]

    if (samplingPriority != PrioritySampling.UNSET) {
      actual.put(SAMPLING_PRIORITY_KEY, String.valueOf(samplingPriority))
    }

    final ExtractedContext context = extractor.extract(new TextMapExtractAdapter(actual))

    expect:
    context.getTraceId() == "1"
    context.getSpanId() == "2"
    context.getBaggage().get("k1") == "v1"
    context.getBaggage().get("k2") == "v2"
    context.getTags() == ["some-tag": "my-interesting-info"]
    context.getSamplingPriority() == samplingPriority

    where:
    samplingPriority              | _
    PrioritySampling.UNSET        | _
    PrioritySampling.SAMPLER_KEEP | _
  }

  def "extract header tags with no propagation"() {
    setup:
    final Map<String, String> actual = [
      SOME_HEADER: "my-interesting-info",
    ]

    TagContext context = extractor.extract(new TextMapExtractAdapter(actual))

    expect:
    !(context instanceof ExtractedContext)
    context.getTags() == ["some-tag": "my-interesting-info"]
  }

  def "extract empty headers returns null"() {
    expect:
    extractor.extract(new TextMapExtractAdapter(["ignored-header": "ignored-value"])) == null
  }

  def "extract http headers with larger than Java long IDs"() {
    setup:
    String largeTraceId = "9523372036854775807"
    String largeSpanId = "15815582334751494918"
    final Map<String, String> actual = [
      (TRACE_ID_KEY.toUpperCase())            : largeTraceId,
      (SPAN_ID_KEY.toUpperCase())             : largeSpanId,
      (OT_BAGGAGE_PREFIX.toUpperCase() + "k1"): "v1",
      (OT_BAGGAGE_PREFIX.toUpperCase() + "k2"): "v2",
      SOME_HEADER                             : "my-interesting-info",
    ]

    if (samplingPriority != PrioritySampling.UNSET) {
      actual.put(SAMPLING_PRIORITY_KEY, String.valueOf(samplingPriority))
    }

    final ExtractedContext context = extractor.extract(new TextMapExtractAdapter(actual))

    expect:
    context.getTraceId() == largeTraceId
    context.getSpanId() == largeSpanId
    context.getBaggage().get("k1") == "v1"
    context.getBaggage().get("k2") == "v2"
    context.getTags() == ["some-tag": "my-interesting-info"]
    context.getSamplingPriority() == samplingPriority

    where:
    samplingPriority              | _
    PrioritySampling.UNSET        | _
    PrioritySampling.SAMPLER_KEEP | _
  }

  def "extract http headers with uint 64 max IDs"() {
    setup:
    String largeSpanId = BIG_INTEGER_UINT64_MAX.subtract(BigInteger.ONE).toString()
    final Map<String, String> actual = [
      (TRACE_ID_KEY.toUpperCase())            : BIG_INTEGER_UINT64_MAX.toString(),
      (SPAN_ID_KEY.toUpperCase())             : BIG_INTEGER_UINT64_MAX.minus(1).toString(),
      (OT_BAGGAGE_PREFIX.toUpperCase() + "k1"): "v1",
      (OT_BAGGAGE_PREFIX.toUpperCase() + "k2"): "v2",
      SOME_HEADER                             : "my-interesting-info",
    ]

    if (samplingPriority != PrioritySampling.UNSET) {
      actual.put(SAMPLING_PRIORITY_KEY, String.valueOf(samplingPriority))
    }

    final ExtractedContext context = extractor.extract(new TextMapExtractAdapter(actual))

    expect:
    context.getTraceId() == BIG_INTEGER_UINT64_MAX.toString()
    context.getSpanId() == largeSpanId
    context.getBaggage().get("k1") == "v1"
    context.getBaggage().get("k2") == "v2"
    context.getTags() == ["some-tag": "my-interesting-info"]
    context.getSamplingPriority() == samplingPriority

    where:
    samplingPriority              | _
    PrioritySampling.UNSET        | _
    PrioritySampling.SAMPLER_KEEP | _
  }

  def "extract http headers with invalid non-numeric ID"() {
    setup:
    final Map<String, String> actual = [
      (TRACE_ID_KEY.toUpperCase())            : "traceID",
      (SPAN_ID_KEY.toUpperCase())             : "spanID",
      (OT_BAGGAGE_PREFIX.toUpperCase() + "k1"): "v1",
      (OT_BAGGAGE_PREFIX.toUpperCase() + "k2"): "v2",
      SOME_HEADER                             : "my-interesting-info",
    ]

    if (samplingPriority != PrioritySampling.UNSET) {
      actual.put(SAMPLING_PRIORITY_KEY, String.valueOf(samplingPriority))
    }

    when:
    extractor.extract(new TextMapExtractAdapter(actual))

    then:
    def iae = thrown(IllegalArgumentException)
    assert iae.cause instanceof NumberFormatException

    where:
    samplingPriority              | _
    PrioritySampling.UNSET        | _
    PrioritySampling.SAMPLER_KEEP | _
  }

  def "extract http headers with out of range trace ID"() {
    setup:
    String outOfRangeTraceId = BIG_INTEGER_UINT64_MAX.add(BigInteger.ONE).toString()
    final Map<String, String> actual = [
      (TRACE_ID_KEY.toUpperCase())            : outOfRangeTraceId,
      (SPAN_ID_KEY.toUpperCase())             : "0",
      (OT_BAGGAGE_PREFIX.toUpperCase() + "k1"): "v1",
      (OT_BAGGAGE_PREFIX.toUpperCase() + "k2"): "v2",
      SOME_HEADER                             : "my-interesting-info",
    ]

    if (samplingPriority != PrioritySampling.UNSET) {
      actual.put(SAMPLING_PRIORITY_KEY, String.valueOf(samplingPriority))
    }

    when:
    extractor.extract(new TextMapExtractAdapter(actual))

    then:
    thrown(IllegalArgumentException)

    where:
    samplingPriority              | _
    PrioritySampling.UNSET        | _
    PrioritySampling.SAMPLER_KEEP | _
  }

  def "extract http headers with out of range span ID"() {
    setup:
    final Map<String, String> actual = [
      (TRACE_ID_KEY.toUpperCase())            : "0",
      (SPAN_ID_KEY.toUpperCase())             : "-1",
      (OT_BAGGAGE_PREFIX.toUpperCase() + "k1"): "v1",
      (OT_BAGGAGE_PREFIX.toUpperCase() + "k2"): "v2",
      SOME_HEADER                             : "my-interesting-info",
    ]

    if (samplingPriority != PrioritySampling.UNSET) {
      actual.put(SAMPLING_PRIORITY_KEY, String.valueOf(samplingPriority))
    }

    when:
    extractor.extract(new TextMapExtractAdapter(actual))

    then:
    thrown(IllegalArgumentException)

    where:
    samplingPriority              | _
    PrioritySampling.UNSET        | _
    PrioritySampling.SAMPLER_KEEP | _
  }
}
