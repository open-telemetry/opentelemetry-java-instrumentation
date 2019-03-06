package datadog.opentracing.propagation

import datadog.trace.api.sampling.PrioritySampling
import io.opentracing.SpanContext
import io.opentracing.propagation.TextMapExtractAdapter
import spock.lang.Specification

import static datadog.opentracing.propagation.DatadogHttpCodec.ORIGIN_KEY
import static datadog.opentracing.propagation.DatadogHttpCodec.OT_BAGGAGE_PREFIX
import static datadog.opentracing.propagation.DatadogHttpCodec.SAMPLING_PRIORITY_KEY
import static datadog.opentracing.propagation.DatadogHttpCodec.SPAN_ID_KEY
import static datadog.opentracing.propagation.DatadogHttpCodec.TRACE_ID_KEY
import static datadog.opentracing.propagation.HttpCodec.UINT64_MAX

class DatadogHttpExtractorTest extends Specification {

  HttpCodec.Extractor extractor = new DatadogHttpCodec.Extractor(["SOME_HEADER": "some-tag"])

  def "extract http headers"() {
    setup:
    final Map<String, String> actual = [
      (TRACE_ID_KEY.toUpperCase())            : traceId,
      (SPAN_ID_KEY.toUpperCase())             : spanId,
      (OT_BAGGAGE_PREFIX.toUpperCase() + "k1"): "v1",
      (OT_BAGGAGE_PREFIX.toUpperCase() + "k2"): "v2",
      SOME_HEADER                             : "my-interesting-info",
    ]

    if (samplingPriority != PrioritySampling.UNSET) {
      actual.put(SAMPLING_PRIORITY_KEY, "$samplingPriority".toString())
    }

    if (origin) {
      actual.put(ORIGIN_KEY, origin)
    }

    when:
    final ExtractedContext context = extractor.extract(new TextMapExtractAdapter(actual))

    then:
    context.traceId == traceId
    context.spanId == spanId
    context.baggage == ["k1": "v1", "k2": "v2"]
    context.tags == ["some-tag": "my-interesting-info"]
    context.samplingPriority == samplingPriority
    context.origin == origin

    where:
    traceId                        | spanId                         | samplingPriority              | origin
    "1"                            | "2"                            | PrioritySampling.UNSET        | null
    "2"                            | "3"                            | PrioritySampling.SAMPLER_KEEP | "saipan"
    UINT64_MAX.toString()          | UINT64_MAX.minus(1).toString() | PrioritySampling.UNSET        | "saipan"
    UINT64_MAX.minus(1).toString() | UINT64_MAX.toString()          | PrioritySampling.SAMPLER_KEEP | "saipan"
  }

  def "extract header tags with no propagation"() {
    when:
    TagContext context = extractor.extract(new TextMapExtractAdapter(headers))

    then:
    !(context instanceof ExtractedContext)
    context.getTags() == ["some-tag": "my-interesting-info"]
    if (headers.containsKey(ORIGIN_KEY)) {
      assert ((TagContext) context).origin == "my-origin"
    }

    where:
    headers                                                         | _
    [SOME_HEADER: "my-interesting-info"]                            | _
    [(ORIGIN_KEY): "my-origin", SOME_HEADER: "my-interesting-info"] | _
  }

  def "extract empty headers returns null"() {
    expect:
    extractor.extract(new TextMapExtractAdapter(["ignored-header": "ignored-value"])) == null
  }

  def "extract http headers with invalid non-numeric ID"() {
    setup:
    final Map<String, String> actual = [
      (TRACE_ID_KEY.toUpperCase())            : "traceId",
      (SPAN_ID_KEY.toUpperCase())             : "spanId",
      (OT_BAGGAGE_PREFIX.toUpperCase() + "k1"): "v1",
      (OT_BAGGAGE_PREFIX.toUpperCase() + "k2"): "v2",
      SOME_HEADER                             : "my-interesting-info",
    ]

    when:
    SpanContext context = extractor.extract(new TextMapExtractAdapter(actual))

    then:
    context == null
  }

  def "extract http headers with out of range trace ID"() {
    setup:
    String outOfRangeTraceId = UINT64_MAX.add(BigInteger.ONE).toString()
    final Map<String, String> actual = [
      (TRACE_ID_KEY.toUpperCase())            : outOfRangeTraceId,
      (SPAN_ID_KEY.toUpperCase())             : "0",
      (OT_BAGGAGE_PREFIX.toUpperCase() + "k1"): "v1",
      (OT_BAGGAGE_PREFIX.toUpperCase() + "k2"): "v2",
      SOME_HEADER                             : "my-interesting-info",
    ]

    when:
    SpanContext context = extractor.extract(new TextMapExtractAdapter(actual))

    then:
    context == null
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

    when:
    SpanContext context = extractor.extract(new TextMapExtractAdapter(actual))

    then:
    context == null
  }
}
