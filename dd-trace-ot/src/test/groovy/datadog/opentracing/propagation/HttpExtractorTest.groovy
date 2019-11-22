package datadog.opentracing.propagation


import datadog.trace.util.test.DDSpecification
import io.opentracing.SpanContext
import io.opentracing.propagation.TextMapExtractAdapter
import spock.lang.Shared

import static datadog.opentracing.DDTracer.TRACE_ID_MAX

class HttpExtractorTest extends DDSpecification {

  @Shared
  String outOfRangeTraceId = (TRACE_ID_MAX + 1).toString()

  def "extract http headers"() {
    setup:
    HttpCodec.Extractor extractor = HttpCodec.createExtractor()

    final Map<String, String> actual = [:]
    if (datadogTraceId != null) {
      actual.put(DatadogHttpCodec.TRACE_ID_KEY.toUpperCase(), datadogTraceId)
    }
    if (datadogSpanId != null) {
      actual.put(DatadogHttpCodec.SPAN_ID_KEY.toUpperCase(), datadogSpanId)
    }

    when:
    final SpanContext context = extractor.extract(new TextMapExtractAdapter(actual))

    then:
    if (expectedTraceId == null) {
      assert context == null
    } else {
      assert context.traceId == expectedTraceId
      assert context.spanId == expectedSpanId
    }

    where:
    datadogTraceId    | datadogSpanId     | expectedTraceId | expectedSpanId
    "1"               | "2"               | 1G              | 2G
    "abc"             | "2"               | null            | null
    outOfRangeTraceId | "2"               | null            | null
    "1"               | outOfRangeTraceId | null            | null
  }

}
