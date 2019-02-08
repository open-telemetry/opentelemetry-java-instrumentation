package datadog.trace.tracer

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

/**
 * Helper class to parse serialized span to verify serialization logic
 */
@EqualsAndHashCode
@ToString
class JsonSpan {
  @JsonProperty("trace_id")
  BigInteger traceId
  @JsonProperty("parent_id")
  BigInteger parentId
  @JsonProperty("span_id")
  BigInteger spanId

  @JsonProperty("start")
  long start
  @JsonProperty("duration")
  long duration

  @JsonProperty("service")
  String service
  @JsonProperty("resource")
  String resource
  @JsonProperty("type")
  String type
  @JsonProperty("name")
  String name

  @JsonProperty("error")
  // Somehow MsgPack formatter can not convert number to boolean with newer jackson so we have to do this manually
  //@JsonFormat(shape = JsonFormat.Shape.NUMBER)
  int error

  @JsonProperty("meta")
  Map<String, String> meta

  @JsonCreator
  JsonSpan() {}

  JsonSpan(SpanImpl span) {
    traceId = new BigInteger(span.getContext().getTraceId())
    parentId = new BigInteger(span.getContext().getParentId())
    spanId = new BigInteger(span.getContext().getSpanId())

    start = span.getStartTimestamp().getTime()
    duration = span.getDuration()

    service = span.getService()
    resource = span.getResource()
    type = span.getType()
    name = span.getName()

    error = span.isErrored() ? 1 : 0

    meta = span.getMetaString()
  }
}
