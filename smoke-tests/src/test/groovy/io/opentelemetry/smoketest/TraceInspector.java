/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import com.google.protobuf.ByteString;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.Span;
import java.util.Collection;
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

  public long countFilteredEventAttributes(String attributeName, Object attributeValue) {
    return getSpanStream()
        .flatMap(s -> s.getEventsList().stream())
        .flatMap(e -> e.getAttributesList().stream())
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

  protected int countSpans() {
    return (int) getSpanStream().count();
  }

  public int size() {
    return traces.size();
  }

  public Set<String> getTraceIds() {
    return getSpanStream()
        .map(Span::getTraceId)
        .map(ByteString::toByteArray)
        .map(TraceInspector::bytesToHex)
        .collect(Collectors.toSet());
  }

  private static String bytesToHex(byte[] bytes) {
    char[] dest = new char[bytes.length * 2];
    bytesToBase16(bytes, dest);
    return new String(dest);
  }

  private static void bytesToBase16(byte[] bytes, char[] dest) {
    for (int i = 0; i < bytes.length; i++) {
      byteToBase16(bytes[i], dest, i * 2);
    }
  }

  private static void byteToBase16(byte value, char[] dest, int destOffset) {
    int b = value & 0xFF;
    dest[destOffset] = ENCODING[b];
    dest[destOffset + 1] = ENCODING[b | 0x100];
  }

  private static final String ALPHABET = "0123456789abcdef";
  private static final char[] ENCODING = buildEncodingArray();

  private static char[] buildEncodingArray() {
    char[] encoding = new char[512];
    for (int i = 0; i < 256; ++i) {
      encoding[i] = ALPHABET.charAt(i >>> 4);
      encoding[i | 0x100] = ALPHABET.charAt(i & 0xF);
    }
    return encoding;
  }
}
