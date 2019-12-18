package datadog.opentracing.propagation

import datadog.trace.util.test.DDSpecification

import static datadog.opentracing.DDTracer.TRACE_ID_MAX
import static datadog.opentracing.propagation.DatadogHttpCodec.SPAN_ID_KEY
import static datadog.opentracing.propagation.DatadogHttpCodec.TRACE_ID_KEY

class DatadogHttpExtractorTest extends DDSpecification {

  HttpCodec.Extractor extractor = new DatadogHttpCodec.Extractor()

  def "extract http headers"() {
    setup:
    def headers = [
      (TRACE_ID_KEY.toUpperCase()): traceId.toString(),
      (SPAN_ID_KEY.toUpperCase()) : spanId.toString()
    ]

    when:
    final ExtractedContext context = extractor.extract(new TextMapExtractAdapter(headers))

    then:
    context.traceId == new BigInteger(traceId)
    context.spanId == new BigInteger(spanId)

    where:
    traceId                       | spanId
    "1"                           | "2"
    TRACE_ID_MAX.toString()       | (TRACE_ID_MAX - 1).toString()
    (TRACE_ID_MAX - 1).toString() | TRACE_ID_MAX.toString()
  }

  def "extract empty headers returns null"() {
    expect:
    extractor.extract(new TextMapExtractAdapter(["ignored-header": "ignored-value"])) == null
  }

  def "extract http headers with invalid non-numeric ID"() {
    setup:
    def headers = [
      (TRACE_ID_KEY.toUpperCase()): "traceId",
      (SPAN_ID_KEY.toUpperCase()) : "spanId"
    ]

    when:
    ExtractedContext context = extractor.extract(new TextMapExtractAdapter(headers))

    then:
    context == null
  }

  def "extract http headers with out of range trace ID"() {
    setup:
    String outOfRangeTraceId = (TRACE_ID_MAX + 1).toString()
    def headers = [
      (TRACE_ID_KEY.toUpperCase()): outOfRangeTraceId,
      (SPAN_ID_KEY.toUpperCase()) : "0"
    ]

    when:
    ExtractedContext context = extractor.extract(new TextMapExtractAdapter(headers))

    then:
    context == null
  }

  def "extract http headers with out of range span ID"() {
    setup:
    def headers = [
      (TRACE_ID_KEY.toUpperCase()): "0",
      (SPAN_ID_KEY.toUpperCase()) : "-1"
    ]

    when:
    ExtractedContext context = extractor.extract(new TextMapExtractAdapter(headers))

    then:
    context == null
  }

  def "more ID range validation"() {
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
    gtTraceId             | gSpanId               | expectedTraceId | expectedSpanId
    "-1"                  | "1"                   | null            | 0G
    "1"                   | "-1"                  | null            | 0G
    "0"                   | "1"                   | null            | 0G
    "1"                   | "0"                   | 1G              | 0G
    "$TRACE_ID_MAX"       | "1"                   | TRACE_ID_MAX    | 1G
    "${TRACE_ID_MAX + 1}" | "1"                   | null            | 1G
    "1"                   | "$TRACE_ID_MAX"       | 1G              | TRACE_ID_MAX
    "1"                   | "${TRACE_ID_MAX + 1}" | null            | 0G

    traceId = gtTraceId.toString()
    spanId = gSpanId.toString()
  }
}
