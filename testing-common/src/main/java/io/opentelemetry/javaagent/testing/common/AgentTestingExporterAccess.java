/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.common;

import static io.opentelemetry.api.common.AttributeKey.booleanArrayKey;
import static io.opentelemetry.api.common.AttributeKey.doubleArrayKey;
import static io.opentelemetry.api.common.AttributeKey.longArrayKey;
import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;

import com.google.protobuf.InvalidProtocolBufferException;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span.Kind;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceId;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.TraceStateBuilder;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.ArrayValue;
import io.opentelemetry.proto.common.v1.InstrumentationLibrary;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.resource.v1.Resource;
import io.opentelemetry.proto.trace.v1.InstrumentationLibrarySpans;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.Span;
import io.opentelemetry.proto.trace.v1.Status;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.testing.trace.TestSpanData;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class AgentTestingExporterAccess {
  private static final char TRACESTATE_KEY_VALUE_DELIMITER = '=';
  private static final char TRACESTATE_ENTRY_DELIMITER = ',';
  private static final Pattern TRACESTATE_ENTRY_DELIMITER_SPLIT_PATTERN =
      Pattern.compile("[ \t]*" + TRACESTATE_ENTRY_DELIMITER + "[ \t]*");

  private static final MethodHandle getExportRequests;
  private static final MethodHandle reset;
  private static final MethodHandle forceFlushCalled;

  static {
    try {
      Class<?> agentTestingExporterFactoryClass =
          AgentClassLoaderAccess.loadClass(
              "io.opentelemetry.javaagent.testing.exporter.AgentTestingExporterFactory");
      MethodHandles.Lookup lookup = MethodHandles.lookup();
      getExportRequests =
          lookup.findStatic(
              agentTestingExporterFactoryClass,
              "getExportRequests",
              MethodType.methodType(List.class));
      reset =
          lookup.findStatic(
              agentTestingExporterFactoryClass, "reset", MethodType.methodType(void.class));
      forceFlushCalled =
          lookup.findStatic(
              agentTestingExporterFactoryClass,
              "forceFlushCalled",
              MethodType.methodType(boolean.class));
    } catch (Exception e) {
      throw new Error("Error accessing fields with reflection.", e);
    }
  }

  public static void reset() {
    try {
      reset.invokeExact();
    } catch (Throwable t) {
      throw new Error("Could not invoke reset", t);
    }
  }

  public static boolean forceFlushCalled() {
    try {
      return (boolean) forceFlushCalled.invokeExact();
    } catch (Throwable t) {
      throw new Error("Could not invoke forceFlushCalled", t);
    }
  }

  @SuppressWarnings("unchecked")
  public static List<SpanData> getExportedSpans() {
    final List<byte[]> exportRequests;
    try {
      exportRequests = (List<byte[]>) getExportRequests.invokeExact();
    } catch (Throwable t) {
      throw new Error("Could not invoke getExportRequests", t);
    }

    List<ResourceSpans> allResourceSpans =
        exportRequests.stream()
            .map(
                serialized -> {
                  try {
                    return ExportTraceServiceRequest.parseFrom(serialized);
                  } catch (InvalidProtocolBufferException e) {
                    throw new Error(e);
                  }
                })
            .flatMap(request -> request.getResourceSpansList().stream())
            .collect(Collectors.toList());
    List<SpanData> spans = new ArrayList<>();
    for (ResourceSpans resourceSpans : allResourceSpans) {
      Resource resource = resourceSpans.getResource();
      for (InstrumentationLibrarySpans ilSpans :
          resourceSpans.getInstrumentationLibrarySpansList()) {
        InstrumentationLibrary instrumentationLibrary = ilSpans.getInstrumentationLibrary();
        for (Span span : ilSpans.getSpansList()) {
          spans.add(
              TestSpanData.builder()
                  .setTraceId(TraceId.bytesToHex(span.getTraceId().toByteArray()))
                  .setSpanId(SpanId.bytesToHex(span.getSpanId().toByteArray()))
                  .setTraceState(extractTraceState(span.getTraceState()))
                  .setParentSpanId(SpanId.bytesToHex(span.getParentSpanId().toByteArray()))
                  .setResource(
                      io.opentelemetry.sdk.resources.Resource.create(
                          fromProto(resource.getAttributesList())))
                  .setInstrumentationLibraryInfo(
                      InstrumentationLibraryInfo.create(
                          instrumentationLibrary.getName(), instrumentationLibrary.getVersion()))
                  .setName(span.getName())
                  .setStartEpochNanos(span.getStartTimeUnixNano())
                  .setEndEpochNanos(span.getEndTimeUnixNano())
                  .setAttributes(fromProto(span.getAttributesList()))
                  .setEvents(
                      span.getEventsList().stream()
                          .map(
                              event ->
                                  SpanData.Event.create(
                                      event.getTimeUnixNano(),
                                      event.getName(),
                                      fromProto(event.getAttributesList()),
                                      event.getDroppedAttributesCount()
                                          + event.getAttributesCount()))
                          .collect(Collectors.toList()))
                  .setStatus(fromProto(span.getStatus()))
                  .setKind(fromProto(span.getKind()))
                  .setLinks(
                      span.getLinksList().stream()
                          .map(
                              link ->
                                  SpanData.Link.create(
                                      SpanContext.create(
                                          TraceId.bytesToHex(link.getTraceId().toByteArray()),
                                          SpanId.bytesToHex(link.getSpanId().toByteArray()),
                                          TraceFlags.getDefault(),
                                          extractTraceState(link.getTraceState())),
                                      fromProto(link.getAttributesList()),
                                      link.getDroppedAttributesCount() + link.getAttributesCount()))
                          .collect(Collectors.toList()))
                  // OTLP doesn't have hasRemoteParent
                  .setHasEnded(true)
                  .setTotalRecordedEvents(span.getEventsCount() + span.getDroppedEventsCount())
                  .setTotalRecordedLinks(span.getLinksCount() + span.getDroppedLinksCount())
                  .setTotalAttributeCount(
                      span.getAttributesCount() + span.getDroppedAttributesCount())
                  .build());
        }
      }
    }
    return spans;
  }

  private static Attributes fromProto(List<KeyValue> attributes) {
    AttributesBuilder converted = Attributes.builder();
    for (KeyValue attribute : attributes) {
      String key = attribute.getKey();
      AnyValue value = attribute.getValue();
      switch (value.getValueCase()) {
        case STRING_VALUE:
          converted.put(key, value.getStringValue());
          break;
        case BOOL_VALUE:
          converted.put(key, value.getBoolValue());
          break;
        case INT_VALUE:
          converted.put(key, value.getIntValue());
          break;
        case DOUBLE_VALUE:
          converted.put(key, value.getDoubleValue());
          break;
        case ARRAY_VALUE:
          {
            ArrayValue array = value.getArrayValue();
            if (array.getValuesCount() != 0) {
              switch (array.getValues(0).getValueCase()) {
                case STRING_VALUE:
                  converted.put(
                      stringArrayKey(key),
                      array.getValuesList().stream()
                          .map(AnyValue::getStringValue)
                          .collect(Collectors.toList()));
                  break;
                case BOOL_VALUE:
                  converted.put(
                      booleanArrayKey(key),
                      array.getValuesList().stream()
                          .map(AnyValue::getBoolValue)
                          .collect(Collectors.toList()));
                  break;
                case INT_VALUE:
                  converted.put(
                      longArrayKey(key),
                      array.getValuesList().stream()
                          .map(AnyValue::getIntValue)
                          .collect(Collectors.toList()));
                  break;
                case DOUBLE_VALUE:
                  converted.put(
                      doubleArrayKey(key),
                      array.getValuesList().stream()
                          .map(AnyValue::getDoubleValue)
                          .collect(Collectors.toList()));
                  break;
                case VALUE_NOT_SET:
                  break;
              }
            }
            break;
          }
        case VALUE_NOT_SET:
          break;
      }
    }
    return converted.build();
  }

  private static SpanData.Status fromProto(Status status) {
    final StatusCode code;
    switch (status.getCode()) {
      case STATUS_CODE_OK:
        code = StatusCode.OK;
        break;
      case STATUS_CODE_ERROR:
        code = StatusCode.ERROR;
        break;
      default:
        code = StatusCode.UNSET;
        break;
    }
    return SpanData.Status.create(code, status.getMessage());
  }

  private static Kind fromProto(Span.SpanKind kind) {
    switch (kind) {
      case SPAN_KIND_INTERNAL:
        return Kind.INTERNAL;
      case SPAN_KIND_SERVER:
        return Kind.SERVER;
      case SPAN_KIND_CLIENT:
        return Kind.CLIENT;
      case SPAN_KIND_PRODUCER:
        return Kind.PRODUCER;
      case SPAN_KIND_CONSUMER:
        return Kind.CONSUMER;
      default:
        return Kind.INTERNAL;
    }
  }

  private static TraceState extractTraceState(String traceStateHeader) {
    if (traceStateHeader.isEmpty()) {
      return TraceState.getDefault();
    }
    TraceStateBuilder traceStateBuilder = TraceState.builder();
    String[] listMembers = TRACESTATE_ENTRY_DELIMITER_SPLIT_PATTERN.split(traceStateHeader);
    // Iterate in reverse order because when call builder set the elements is added in the
    // front of the list.
    for (int i = listMembers.length - 1; i >= 0; i--) {
      String listMember = listMembers[i];
      int index = listMember.indexOf(TRACESTATE_KEY_VALUE_DELIMITER);
      traceStateBuilder.set(listMember.substring(0, index), listMember.substring(index + 1));
    }
    return traceStateBuilder.build();
  }

  private AgentTestingExporterAccess() {}
}
