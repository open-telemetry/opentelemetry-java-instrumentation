package datadog.opentracing.propagation

import datadog.trace.api.Config
import datadog.trace.util.test.DDSpecification
import io.opentracing.SpanContext
import io.opentracing.propagation.TextMapExtractAdapter
import spock.lang.Shared

import static datadog.opentracing.DDTracer.TRACE_ID_MAX
import static datadog.trace.api.Config.PropagationStyle.DATADOG

class HttpExtractorTest extends DDSpecification {

  @Shared
  String outOfRangeTraceId = (TRACE_ID_MAX + 1).toString()

  def "extract http headers"() {
    setup:
    Config config = Mock(Config) {
      getPropagationStylesToExtract() >> styles
    }
    HttpCodec.Extractor extractor = HttpCodec.createExtractor(config)

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
    styles    | datadogTraceId    | datadogSpanId     | expectedTraceId | expectedSpanId
    [DATADOG] | "1"               | "2"               | 1G              | 2G
    []        | "1"               | "2"               | null            | null
    [DATADOG] | "abc"             | "2"               | null            | null
    [DATADOG] | outOfRangeTraceId | "2"               | null            | null
    [DATADOG] | "1"               | outOfRangeTraceId | null            | null
    [DATADOG] | "1"               | "2"               | 1G              | 2G
  }

}
