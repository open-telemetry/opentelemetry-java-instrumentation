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
    def headers = [
      (TRACE_ID_KEY.toUpperCase()): traceId.toString(16).toLowerCase(),
      (SPAN_ID_KEY.toUpperCase()) : spanId.toString(16).toLowerCase(),
      SOME_HEADER                 : "my-interesting-info",
    ]

    if (samplingPriority != null) {
      headers.put(SAMPLING_PRIORITY_KEY, "$samplingPriority".toString())
    }

    when:
    final ExtractedContext context = extractor.extract(new TextMapExtractAdapter(headers))

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

  def "extract 128 bit id truncates id to 64 bit"() {
    setup:
    def headers = [
      (TRACE_ID_KEY.toUpperCase()): traceId,
      (SPAN_ID_KEY.toUpperCase()) : spanId,
    ]

    when:
    final ExtractedContext context = extractor.extract(new TextMapExtractAdapter(headers))

    then:
    if (expectedTraceId) {
      assert context.traceId == expectedTraceId
      assert context.spanId == expectedSpanId
    } else {
      assert context == null
    }

    where:
    traceId                             | spanId                   | expectedTraceId       | expectedSpanId
    "-1"                                | "1"                      | null                  | "0"
    "1"                                 | "-1"                     | null                  | "0"
    "0"                                 | "1"                      | null                  | "0"
    "00001"                             | "00001"                  | "1"                   | "1"
    "463ac35c9f6413ad"                  | "463ac35c9f6413ad"       | "5060571933882717101" | "5060571933882717101"
    "463ac35c9f6413ad48485a3953bb6124"  | "1"                      | "5208512171318403364" | "1"
    "f".multiply(16)                    | "1"                      | "$UINT64_MAX"         | "1"
    "a".multiply(16) + "f".multiply(16) | "1"                      | "$UINT64_MAX"         | "1"
    "1" + "f".multiply(32)              | "1"                      | null                  | "1"
    "0" + "f".multiply(32)              | "1"                      | null                  | "1"
    "1"                                 | "f".multiply(16)         | "1"                   | "$UINT64_MAX"
    "1"                                 | "1" + "f".multiply(16)   | null                  | "0"
    "1"                                 | "000" + "f".multiply(16) | "1"                   | "$UINT64_MAX"
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
    def headers = [
      (TRACE_ID_KEY.toUpperCase()): "traceId",
      (SPAN_ID_KEY.toUpperCase()) : "spanId",
      SOME_HEADER                 : "my-interesting-info",
    ]

    when:
    SpanContext context = extractor.extract(new TextMapExtractAdapter(headers))

    then:
    context == null
  }

  def "extract http headers with out of range span ID"() {
    setup:
    def headers = [
      (TRACE_ID_KEY.toUpperCase()): "0",
      (SPAN_ID_KEY.toUpperCase()) : "-1",
      SOME_HEADER                 : "my-interesting-info",
    ]


    when:
    SpanContext context = extractor.extract(new TextMapExtractAdapter(headers))

    then:
    context == null
  }
}
