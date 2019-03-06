package datadog.opentracing.propagation

import datadog.trace.api.Config
import io.opentracing.SpanContext
import io.opentracing.propagation.TextMapExtractAdapter
import spock.lang.Shared
import spock.lang.Specification

import static datadog.opentracing.propagation.HttpCodec.UINT64_MAX

class HttpExtractorTest extends Specification {

  @Shared
  String outOfRangeTraceId = UINT64_MAX.add(BigInteger.ONE)

  def "extract http headers"() {
    setup:
    Config config = Mock(Config) {
      isExtractDatadogHeaders() >> datadogEnabled
      isExtractB3Headers() >> b3Enabled
    }
    HttpCodec.Extractor extractor = HttpCodec.createExtractor(config, ["SOME_HEADER": "some-tag"])

    final Map<String, String> actual = [:]
    if (datadogTraceId != null) {
      actual.put(DatadogHttpCodec.TRACE_ID_KEY.toUpperCase(), datadogTraceId)
    }
    if (datadogSpanId != null) {
      actual.put(DatadogHttpCodec.SPAN_ID_KEY.toUpperCase(), datadogSpanId)
    }
    if (b3TraceId != null) {
      actual.put(B3HttpCodec.TRACE_ID_KEY.toUpperCase(), b3TraceId)
    }
    if (b3SpanId != null) {
      actual.put(B3HttpCodec.SPAN_ID_KEY.toUpperCase(), b3SpanId)
    }

    if (putDatadogFields) {
      actual.put("SOME_HEADER", "my-interesting-info")
    }

    when:
    final SpanContext context = extractor.extract(new TextMapExtractAdapter(actual))

    then:
    if (tagContext) {
      assert context instanceof TagContext
    } else {
      if (expectedTraceId == null) {
        assert context == null
      } else {
        assert context.traceId == expectedTraceId
        assert context.spanId == expectedSpanId
      }
    }

    if (expectDatadogFields) {
      assert context.tags == ["some-tag": "my-interesting-info"]
    }

    where:
    datadogEnabled | b3Enabled | datadogTraceId               | datadogSpanId                | b3TraceId                    | b3SpanId                     | expectedTraceId | expectedSpanId | putDatadogFields | expectDatadogFields | tagContext
    true           | true      | "1"                          | "2"                          | "a"                          | "b"                          | "1"             | "2"            | true             | true                | false
    true           | true      | null                         | null                         | "a"                          | "b"                          | "a"             | "b"            | false            | false               | true
    true           | true      | null                         | null                         | "a"                          | "b"                          | null            | null           | true             | true                | true
    true           | false     | "1"                          | "2"                          | "a"                          | "b"                          | "1"             | "2"            | true             | true                | false
    false          | true      | "1"                          | "2"                          | "a"                          | "b"                          | "10"            | "11"           | false            | false               | false
    false          | false     | "1"                          | "2"                          | "a"                          | "b"                          | null            | null           | false            | false               | false
    true           | true      | "abc"                        | "2"                          | "a"                          | "b"                          | "10"            | "11"           | false            | false               | false
    true           | false     | "abc"                        | "2"                          | "a"                          | "b"                          | null            | null           | false            | false               | false

    true           | true      | outOfRangeTraceId.toString() | "2"                          | "a"                          | "b"                          | "10"            | "11"           | false            | false               | false
    true           | true      | "1"                          | outOfRangeTraceId.toString() | "a"                          | "b"                          | "10"            | "11"           | false            | false               | false
    true           | false     | outOfRangeTraceId.toString() | "2"                          | "a"                          | "b"                          | null            | null           | false            | false               | false
    true           | false     | "1"                          | outOfRangeTraceId.toString() | "a"                          | "b"                          | null            | null           | false            | false               | false
    true           | true      | "1"                          | "2"                          | outOfRangeTraceId.toString() | "b"                          | "1"             | "2"            | true             | false               | false
    true           | true      | "1"                          | "2"                          | "a"                          | outOfRangeTraceId.toString() | "1"             | "2"            | true             | false               | false
  }

}
