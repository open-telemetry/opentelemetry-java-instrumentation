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
    HttpCodec.Extractor extractor = HttpCodec.createExtractor(config, ["SOME_HEADER": "some-tag"])

    final Map<String, String> actual = [:]
    if (datadogTraceId != null) {
      actual.put(DatadogHttpCodec.TRACE_ID_KEY.toUpperCase(), datadogTraceId)
    }
    if (datadogSpanId != null) {
      actual.put(DatadogHttpCodec.SPAN_ID_KEY.toUpperCase(), datadogSpanId)
    }

    if (putDatadogFields) {
      actual.put("SOME_HEADER", "my-interesting-info")
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

    if (expectDatadogFields) {
      assert context.tags == ["some-tag": "my-interesting-info"]
    }

    where:
    styles    | datadogTraceId    | datadogSpanId     | expectedTraceId | expectedSpanId | putDatadogFields | expectDatadogFields
    [DATADOG] | "1"               | "2"               | 1G              | 2G             | true             | true
    [DATADOG] | "1"               | "2"               | 1G              | 2G             | true             | true
    []        | "1"               | "2"               | null            | null           | false            | false
    [DATADOG] | "abc"             | "2"               | null            | null           | false            | false
    [DATADOG] | outOfRangeTraceId | "2"               | null            | null           | false            | false
    [DATADOG] | "1"               | outOfRangeTraceId | null            | null           | false            | false
    [DATADOG] | "1"               | "2"               | 1G              | 2G             | true             | false
  }

}
