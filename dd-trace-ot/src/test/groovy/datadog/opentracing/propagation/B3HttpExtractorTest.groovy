package datadog.opentracing.propagation

import datadog.trace.api.sampling.PrioritySampling
import io.opentracing.SpanContext
import io.opentracing.propagation.TextMapExtractAdapter
import spock.lang.Specification

import static datadog.opentracing.propagation.B3HttpCodec.SAMPLING_PRIORITY_KEY
import static datadog.opentracing.propagation.B3HttpCodec.SPAN_ID_KEY
import static datadog.opentracing.propagation.B3HttpCodec.TRACE_ID_KEY
import static datadog.opentracing.propagation.HttpCodec.UINT64_MAX

class B3HttpExtractorTest extends Specification {

  HttpCodec.Extractor extractor = new B3HttpCodec.Extractor(["SOME_HEADER": "some-tag"])

  def "extract http headers"() {
    setup:
    final Map<String, String> actual = [
      (TRACE_ID_KEY.toUpperCase()): traceId.toString(16).toLowerCase(),
      (SPAN_ID_KEY.toUpperCase()) : spanId.toString(16).toLowerCase(),
      SOME_HEADER                 : "my-interesting-info",
    ]

    if (samplingPriority != null) {
      actual.put(SAMPLING_PRIORITY_KEY, "$samplingPriority".toString())
    }

    when:
    final ExtractedContext context = extractor.extract(new TextMapExtractAdapter(actual))

    then:
    context.traceId == traceId.toString()
    context.spanId == spanId.toString()
    context.baggage == [:]
    context.tags == ["some-tag": "my-interesting-info"]
    context.samplingPriority == expectedSamplingPriority
    context.origin == null

    where:
    traceId             | spanId              | samplingPriority | expectedSamplingPriority
    1G                  | 2G                  | null             | PrioritySampling.UNSET
    2G                  | 3G                  | 1                | PrioritySampling.SAMPLER_KEEP
    3G                  | 4G                  | 0                | PrioritySampling.SAMPLER_DROP
    UINT64_MAX          | UINT64_MAX.minus(1) | 0                | PrioritySampling.SAMPLER_DROP
    UINT64_MAX.minus(1) | UINT64_MAX          | 1                | PrioritySampling.SAMPLER_KEEP
  }

  def "extract header tags with no propagation"() {
    when:
    TagContext context = extractor.extract(new TextMapExtractAdapter(headers))

    then:
    !(context instanceof ExtractedContext)
    context.getTags() == ["some-tag": "my-interesting-info"]

    where:
    headers                              | _
    [SOME_HEADER: "my-interesting-info"] | _
  }

  def "extract empty headers returns null"() {
    expect:
    extractor.extract(new TextMapExtractAdapter(["ignored-header": "ignored-value"])) == null
  }

  def "extract http headers with invalid non-numeric ID"() {
    setup:
    final Map<String, String> actual = [
      (TRACE_ID_KEY.toUpperCase()): "traceId",
      (SPAN_ID_KEY.toUpperCase()) : "spanId",
      SOME_HEADER                 : "my-interesting-info",
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
      (TRACE_ID_KEY.toUpperCase()): outOfRangeTraceId,
      (SPAN_ID_KEY.toUpperCase()) : "0",
      SOME_HEADER                 : "my-interesting-info",
    ]

    when:
    SpanContext context = extractor.extract(new TextMapExtractAdapter(actual))

    then:
    context == null
  }

  def "extract http headers with out of range span ID"() {
    setup:
    final Map<String, String> actual = [
      (TRACE_ID_KEY.toUpperCase()): "0",
      (SPAN_ID_KEY.toUpperCase()) : "-1",
      SOME_HEADER                 : "my-interesting-info",
    ]


    when:
    SpanContext context = extractor.extract(new TextMapExtractAdapter(actual))

    then:
    context == null
  }
}
