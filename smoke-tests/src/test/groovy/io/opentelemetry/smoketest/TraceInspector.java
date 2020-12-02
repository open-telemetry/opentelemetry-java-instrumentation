package io.opentelemetry.smoketest;

import com.google.protobuf.ByteString;
import io.opentelemetry.api.trace.TraceId;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.Span;
import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TraceInspector {
  final Collection<ExportTraceServiceRequest> traces;

  public TraceInspector(Collection<ExportTraceServiceRequest> traces) {
    this.traces = traces;
  }

  public Stream<Span> getSpanStream() {
    return traces.stream()
        .flatMap(it -> it.getResourceSpansList().stream())
        .flatMap(it -> it.getInstrumentationLibrarySpansList().stream())
        .flatMap(it -> it.getSpansList().stream());
  }

  public Stream<AnyValue> findResourceAttribute(String attributeKey) {
    return traces.stream()
        .flatMap(it -> it.getResourceSpansList().stream())
        .flatMap(it -> it.getResource().getAttributesList().stream())
        .filter(it -> it.getKey().equals(attributeKey))
        .map(KeyValue::getValue);
  }

  public long countFilteredResourceAttributes(String attributeName, Object attributeValue) {
    return traces.stream()
        .flatMap(it -> it.getResourceSpansList().stream())
        .map(ResourceSpans::getResource)
        .flatMap(it -> it.getAttributesList().stream())
        .filter(a -> a.getKey().equals(attributeName))
        .map(a -> a.getValue().getStringValue())
        .filter(s -> s.equals(attributeValue))
        .count();
  }

  public long countFilteredAttributes(String attributeName, Object attributeValue) {
    return getSpanStream()
        .flatMap(s -> s.getAttributesList().stream())
        .filter(a -> a.getKey().equals(attributeName))
        .map(a -> a.getValue().getStringValue())
        .filter(s -> s.equals(attributeValue))
        .count();
  }

  protected int countSpansByName(String spanName) {
    return (int) getSpanStream().filter(it -> it.getName().equals(spanName)).count();
  }

  protected int countSpansByKind(Span.SpanKind spanKind) {
    return (int) getSpanStream().filter(it -> it.getKind().equals(spanKind)).count();
  }

  public int size() {
    return traces.size();
  }

  public Set<String> getTraceIds() {
    return getSpanStream()
        .map(Span::getTraceId)
        .map(ByteString::toByteArray)
        .map(TraceId::bytesToHex)
        .collect(Collectors.toSet());
  }

  /**
   * This method returns the value for the requested attribute of the *first* server span. Be
   * careful when using on a distributed trace with several server spans.
   */
  public String getServerSpanAttribute(String attributeKey) {
    return getSpanStream()
        .filter(span -> span.getKind() == Span.SpanKind.SPAN_KIND_SERVER)
        .map(Span::getAttributesList)
        .flatMap(Collection::stream)
        .filter(attr -> attributeKey.equals(attr.getKey()))
        .map(keyValue -> keyValue.getValue().getStringValue())
        .findFirst()
        .orElseThrow(
            () ->
                new NoSuchElementException(
                    "Attribute " + attributeKey + " is not found on server span"));
  }
}
