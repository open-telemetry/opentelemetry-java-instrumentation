/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.common;

import static io.opentelemetry.api.common.AttributeKey.booleanArrayKey;
import static io.opentelemetry.api.common.AttributeKey.doubleArrayKey;
import static io.opentelemetry.api.common.AttributeKey.longArrayKey;
import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static java.util.stream.Collectors.toList;

import com.google.protobuf.InvalidProtocolBufferException;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.TraceStateBuilder;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.ArrayValue;
import io.opentelemetry.proto.common.v1.InstrumentationLibrary;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.logs.v1.InstrumentationLibraryLogs;
import io.opentelemetry.proto.logs.v1.LogRecord;
import io.opentelemetry.proto.logs.v1.ResourceLogs;
import io.opentelemetry.proto.logs.v1.SeverityNumber;
import io.opentelemetry.proto.metrics.v1.HistogramDataPoint;
import io.opentelemetry.proto.metrics.v1.InstrumentationLibraryMetrics;
import io.opentelemetry.proto.metrics.v1.Metric;
import io.opentelemetry.proto.metrics.v1.NumberDataPoint;
import io.opentelemetry.proto.metrics.v1.ResourceMetrics;
import io.opentelemetry.proto.metrics.v1.Sum;
import io.opentelemetry.proto.metrics.v1.SummaryDataPoint;
import io.opentelemetry.proto.resource.v1.Resource;
import io.opentelemetry.proto.trace.v1.InstrumentationLibrarySpans;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.Span;
import io.opentelemetry.proto.trace.v1.Status;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.logs.data.LogData;
import io.opentelemetry.sdk.logs.data.LogDataBuilder;
import io.opentelemetry.sdk.logs.data.Severity;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.DoubleGaugeData;
import io.opentelemetry.sdk.metrics.data.DoubleHistogramData;
import io.opentelemetry.sdk.metrics.data.DoubleHistogramPointData;
import io.opentelemetry.sdk.metrics.data.DoublePointData;
import io.opentelemetry.sdk.metrics.data.DoubleSumData;
import io.opentelemetry.sdk.metrics.data.DoubleSummaryData;
import io.opentelemetry.sdk.metrics.data.DoubleSummaryPointData;
import io.opentelemetry.sdk.metrics.data.LongGaugeData;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.LongSumData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.ValueAtPercentile;
import io.opentelemetry.sdk.testing.trace.TestSpanData;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class AgentTestingExporterAccess {
  private static final char TRACESTATE_KEY_VALUE_DELIMITER = '=';
  private static final char TRACESTATE_ENTRY_DELIMITER = ',';
  private static final Pattern TRACESTATE_ENTRY_DELIMITER_SPLIT_PATTERN =
      Pattern.compile("[ \t]*" + TRACESTATE_ENTRY_DELIMITER + "[ \t]*");

  private static final MethodHandle getSpanExportRequests;
  private static final MethodHandle getMetricExportRequests;
  private static final MethodHandle getLogExportRequests;
  private static final MethodHandle reset;
  private static final MethodHandle forceFlushCalled;

  static {
    try {
      Class<?> agentTestingExporterFactoryClass =
          AgentClassLoaderAccess.loadClass(
              "io.opentelemetry.javaagent.testing.exporter.AgentTestingExporterFactory");
      MethodHandles.Lookup lookup = MethodHandles.lookup();
      getSpanExportRequests =
          lookup.findStatic(
              agentTestingExporterFactoryClass,
              "getSpanExportRequests",
              MethodType.methodType(List.class));
      getMetricExportRequests =
          lookup.findStatic(
              agentTestingExporterFactoryClass,
              "getMetricExportRequests",
              MethodType.methodType(List.class));
      getLogExportRequests =
          lookup.findStatic(
              agentTestingExporterFactoryClass,
              "getLogExportRequests",
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
      throw new AssertionError("Error accessing fields with reflection.", e);
    }
  }

  public static void reset() {
    try {
      reset.invokeExact();
    } catch (Throwable t) {
      throw new AssertionError("Could not invoke reset", t);
    }
  }

  public static boolean forceFlushCalled() {
    try {
      return (boolean) forceFlushCalled.invokeExact();
    } catch (Throwable t) {
      throw new AssertionError("Could not invoke forceFlushCalled", t);
    }
  }

  @SuppressWarnings("unchecked")
  public static List<SpanData> getExportedSpans() {
    List<byte[]> exportRequests;
    try {
      exportRequests = (List<byte[]>) getSpanExportRequests.invokeExact();
    } catch (Throwable t) {
      throw new AssertionError("Could not invoke getSpanExportRequests", t);
    }

    List<ResourceSpans> allResourceSpans =
        exportRequests.stream()
            .map(
                serialized -> {
                  try {
                    return ExportTraceServiceRequest.parseFrom(serialized);
                  } catch (InvalidProtocolBufferException e) {
                    throw new AssertionError(e);
                  }
                })
            .flatMap(request -> request.getResourceSpansList().stream())
            .collect(toList());
    List<SpanData> spans = new ArrayList<>();
    for (ResourceSpans resourceSpans : allResourceSpans) {
      Resource resource = resourceSpans.getResource();
      for (InstrumentationLibrarySpans ilSpans :
          resourceSpans.getInstrumentationLibrarySpansList()) {
        InstrumentationLibrary instrumentationLibrary = ilSpans.getInstrumentationLibrary();
        for (Span span : ilSpans.getSpansList()) {
          String traceId = bytesToHex(span.getTraceId().toByteArray());
          spans.add(
              TestSpanData.builder()
                  .setSpanContext(
                      SpanContext.create(
                          traceId,
                          bytesToHex(span.getSpanId().toByteArray()),
                          TraceFlags.getDefault(),
                          extractTraceState(span.getTraceState())))
                  // TODO is it ok to use default trace flags and default trace state here?
                  .setParentSpanContext(
                      SpanContext.create(
                          traceId,
                          bytesToHex(span.getParentSpanId().toByteArray()),
                          TraceFlags.getDefault(),
                          TraceState.getDefault()))
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
                                  EventData.create(
                                      event.getTimeUnixNano(),
                                      event.getName(),
                                      fromProto(event.getAttributesList()),
                                      event.getDroppedAttributesCount()
                                          + event.getAttributesCount()))
                          .collect(toList()))
                  .setStatus(fromProto(span.getStatus()))
                  .setKind(fromProto(span.getKind()))
                  .setLinks(
                      span.getLinksList().stream()
                          .map(
                              link ->
                                  LinkData.create(
                                      SpanContext.create(
                                          bytesToHex(link.getTraceId().toByteArray()),
                                          bytesToHex(link.getSpanId().toByteArray()),
                                          TraceFlags.getDefault(),
                                          extractTraceState(link.getTraceState())),
                                      fromProto(link.getAttributesList()),
                                      link.getDroppedAttributesCount() + link.getAttributesCount()))
                          .collect(toList()))
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

  @SuppressWarnings("unchecked")
  public static List<MetricData> getExportedMetrics() {
    List<byte[]> exportRequests;
    try {
      exportRequests = (List<byte[]>) getMetricExportRequests.invokeExact();
    } catch (Throwable t) {
      throw new AssertionError("Could not invoke getMetricExportRequests", t);
    }

    List<ResourceMetrics> allResourceMetrics =
        exportRequests.stream()
            .map(
                serialized -> {
                  try {
                    return ExportMetricsServiceRequest.parseFrom(serialized);
                  } catch (InvalidProtocolBufferException e) {
                    throw new AssertionError(e);
                  }
                })
            .flatMap(request -> request.getResourceMetricsList().stream())
            .collect(toList());
    List<MetricData> metrics = new ArrayList<>();
    for (ResourceMetrics resourceMetrics : allResourceMetrics) {
      Resource resource = resourceMetrics.getResource();
      for (InstrumentationLibraryMetrics ilMetrics :
          resourceMetrics.getInstrumentationLibraryMetricsList()) {
        InstrumentationLibrary instrumentationLibrary = ilMetrics.getInstrumentationLibrary();
        for (Metric metric : ilMetrics.getMetricsList()) {
          metrics.add(
              createMetricData(
                  metric,
                  io.opentelemetry.sdk.resources.Resource.create(
                      fromProto(resource.getAttributesList())),
                  InstrumentationLibraryInfo.create(
                      instrumentationLibrary.getName(), instrumentationLibrary.getVersion())));
        }
      }
    }
    return metrics;
  }

  @SuppressWarnings("unchecked")
  public static List<LogData> getExportedLogs() {
    List<byte[]> exportRequests;
    try {
      exportRequests = (List<byte[]>) getLogExportRequests.invokeExact();
    } catch (Throwable t) {
      throw new AssertionError("Could not invoke getMetricExportRequests", t);
    }

    List<ResourceLogs> allResourceLogs =
        exportRequests.stream()
            .map(
                serialized -> {
                  try {
                    return ExportLogsServiceRequest.parseFrom(serialized);
                  } catch (InvalidProtocolBufferException e) {
                    throw new AssertionError(e);
                  }
                })
            .flatMap(request -> request.getResourceLogsList().stream())
            .collect(toList());
    List<LogData> logs = new ArrayList<>();
    for (ResourceLogs resourceLogs : allResourceLogs) {
      Resource resource = resourceLogs.getResource();
      for (InstrumentationLibraryLogs ilLogs : resourceLogs.getInstrumentationLibraryLogsList()) {
        InstrumentationLibrary instrumentationLibrary = ilLogs.getInstrumentationLibrary();
        for (LogRecord logRecord : ilLogs.getLogsList()) {
          logs.add(
              createLogData(
                  logRecord,
                  io.opentelemetry.sdk.resources.Resource.create(
                      fromProto(resource.getAttributesList())),
                  InstrumentationLibraryInfo.create(
                      instrumentationLibrary.getName(), instrumentationLibrary.getVersion())));
        }
      }
    }
    return logs;
  }

  private static MetricData createMetricData(
      Metric metric,
      io.opentelemetry.sdk.resources.Resource resource,
      InstrumentationLibraryInfo instrumentationLibraryInfo) {
    switch (metric.getDataCase()) {
      case GAUGE:
        if (isDouble(metric.getGauge().getDataPointsList())) {
          return MetricData.createDoubleGauge(
              resource,
              instrumentationLibraryInfo,
              metric.getName(),
              metric.getDescription(),
              metric.getUnit(),
              DoubleGaugeData.create(getDoublePointDatas(metric.getGauge().getDataPointsList())));
        } else {
          return MetricData.createLongGauge(
              resource,
              instrumentationLibraryInfo,
              metric.getName(),
              metric.getDescription(),
              metric.getUnit(),
              LongGaugeData.create(getLongPointDatas(metric.getGauge().getDataPointsList())));
        }
      case SUM:
        if (isDouble(metric.getSum().getDataPointsList())) {
          Sum doubleSum = metric.getSum();
          return MetricData.createDoubleSum(
              resource,
              instrumentationLibraryInfo,
              metric.getName(),
              metric.getDescription(),
              metric.getUnit(),
              DoubleSumData.create(
                  doubleSum.getIsMonotonic(),
                  getTemporality(doubleSum.getAggregationTemporality()),
                  getDoublePointDatas(metric.getSum().getDataPointsList())));
        } else {
          Sum longSum = metric.getSum();
          return MetricData.createLongSum(
              resource,
              instrumentationLibraryInfo,
              metric.getName(),
              metric.getDescription(),
              metric.getUnit(),
              LongSumData.create(
                  longSum.getIsMonotonic(),
                  getTemporality(longSum.getAggregationTemporality()),
                  getLongPointDatas(metric.getSum().getDataPointsList())));
        }
      case HISTOGRAM:
        return MetricData.createDoubleHistogram(
            resource,
            instrumentationLibraryInfo,
            metric.getName(),
            metric.getDescription(),
            metric.getUnit(),
            DoubleHistogramData.create(
                getTemporality(metric.getHistogram().getAggregationTemporality()),
                getDoubleHistogramDataPoints(metric.getHistogram().getDataPointsList())));
      case SUMMARY:
        return MetricData.createDoubleSummary(
            resource,
            instrumentationLibraryInfo,
            metric.getName(),
            metric.getDescription(),
            metric.getUnit(),
            DoubleSummaryData.create(
                getDoubleSummaryDataPoints(metric.getSummary().getDataPointsList())));
      default:
        throw new AssertionError("Unexpected metric data: " + metric.getDataCase());
    }
  }

  private static LogData createLogData(
      LogRecord logRecord,
      io.opentelemetry.sdk.resources.Resource resource,
      InstrumentationLibraryInfo instrumentationLibraryInfo) {
    return LogDataBuilder.create(resource, instrumentationLibraryInfo)
        .setEpoch(logRecord.getTimeUnixNano(), TimeUnit.NANOSECONDS)
        .setSpanContext(
            SpanContext.create(
                bytesToHex(logRecord.getTraceId().toByteArray()),
                bytesToHex(logRecord.getSpanId().toByteArray()),
                TraceFlags.getDefault(),
                TraceState.getDefault()))
        .setSeverity(fromProto(logRecord.getSeverityNumber()))
        .setSeverityText(logRecord.getSeverityText())
        .setName(logRecord.getName())
        .setBody(logRecord.getBody().getStringValue())
        .setAttributes(fromProto(logRecord.getAttributesList()))
        .build();
  }

  private static boolean isDouble(List<NumberDataPoint> points) {
    if (points.isEmpty()) {
      return true;
    }
    return points.get(0).getValueCase() == NumberDataPoint.ValueCase.AS_DOUBLE;
  }

  private static List<DoublePointData> getDoublePointDatas(List<NumberDataPoint> points) {
    return points.stream()
        .map(
            point -> {
              double value;
              switch (point.getValueCase()) {
                case AS_INT:
                  value = point.getAsInt();
                  break;
                case AS_DOUBLE:
                default:
                  value = point.getAsDouble();
                  break;
              }
              return DoublePointData.create(
                  point.getStartTimeUnixNano(),
                  point.getTimeUnixNano(),
                  fromProto(point.getAttributesList()),
                  value);
            })
        .collect(toList());
  }

  private static List<LongPointData> getLongPointDatas(List<NumberDataPoint> points) {
    return points.stream()
        .map(
            point -> {
              long value;
              switch (point.getValueCase()) {
                case AS_INT:
                  value = point.getAsInt();
                  break;
                case AS_DOUBLE:
                default:
                  value = (long) point.getAsDouble();
                  break;
              }
              return LongPointData.create(
                  point.getStartTimeUnixNano(),
                  point.getTimeUnixNano(),
                  fromProto(point.getAttributesList()),
                  value);
            })
        .collect(toList());
  }

  private static Collection<DoubleHistogramPointData> getDoubleHistogramDataPoints(
      List<HistogramDataPoint> dataPointsList) {
    return dataPointsList.stream()
        .map(
            point ->
                DoubleHistogramPointData.create(
                    point.getStartTimeUnixNano(),
                    point.getTimeUnixNano(),
                    fromProto(point.getAttributesList()),
                    point.getSum(),
                    point.getExplicitBoundsList(),
                    point.getBucketCountsList()))
        .collect(toList());
  }

  private static Collection<DoubleSummaryPointData> getDoubleSummaryDataPoints(
      List<SummaryDataPoint> dataPointsList) {
    return dataPointsList.stream()
        .map(
            point ->
                DoubleSummaryPointData.create(
                    point.getStartTimeUnixNano(),
                    point.getTimeUnixNano(),
                    fromProto(point.getAttributesList()),
                    point.getCount(),
                    point.getSum(),
                    getValues(point)))
        .collect(toList());
  }

  private static List<ValueAtPercentile> getValues(SummaryDataPoint point) {
    return point.getQuantileValuesList().stream()
        .map(v -> ValueAtPercentile.create(v.getQuantile(), v.getValue()))
        .collect(Collectors.toList());
  }

  private static AggregationTemporality getTemporality(
      io.opentelemetry.proto.metrics.v1.AggregationTemporality aggregationTemporality) {
    switch (aggregationTemporality) {
      case AGGREGATION_TEMPORALITY_CUMULATIVE:
        return AggregationTemporality.CUMULATIVE;
      case AGGREGATION_TEMPORALITY_DELTA:
        return AggregationTemporality.DELTA;
      default:
        throw new IllegalStateException(
            "Unexpected aggregation temporality: " + aggregationTemporality);
    }
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
          ArrayValue array = value.getArrayValue();
          if (array.getValuesCount() != 0) {
            switch (array.getValues(0).getValueCase()) {
              case STRING_VALUE:
                converted.put(
                    stringArrayKey(key),
                    array.getValuesList().stream().map(AnyValue::getStringValue).collect(toList()));
                break;
              case BOOL_VALUE:
                converted.put(
                    booleanArrayKey(key),
                    array.getValuesList().stream().map(AnyValue::getBoolValue).collect(toList()));
                break;
              case INT_VALUE:
                converted.put(
                    longArrayKey(key),
                    array.getValuesList().stream().map(AnyValue::getIntValue).collect(toList()));
                break;
              case DOUBLE_VALUE:
                converted.put(
                    doubleArrayKey(key),
                    array.getValuesList().stream().map(AnyValue::getDoubleValue).collect(toList()));
                break;
              case VALUE_NOT_SET:
                break;
              default:
                throw new IllegalStateException(
                    "Unexpected attribute: " + array.getValues(0).getValueCase());
            }
          }
          break;
        case VALUE_NOT_SET:
          break;
        default:
          throw new IllegalStateException("Unexpected attribute: " + value.getValueCase());
      }
    }
    return converted.build();
  }

  private static StatusData fromProto(Status status) {
    StatusCode code;
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
    return StatusData.create(code, status.getMessage());
  }

  private static SpanKind fromProto(Span.SpanKind kind) {
    switch (kind) {
      case SPAN_KIND_INTERNAL:
        return SpanKind.INTERNAL;
      case SPAN_KIND_SERVER:
        return SpanKind.SERVER;
      case SPAN_KIND_CLIENT:
        return SpanKind.CLIENT;
      case SPAN_KIND_PRODUCER:
        return SpanKind.PRODUCER;
      case SPAN_KIND_CONSUMER:
        return SpanKind.CONSUMER;
      default:
        throw new IllegalArgumentException("Unexpected span kind: " + kind);
    }
  }

  private static Severity fromProto(SeverityNumber proto) {
    for (Severity severity : Severity.values()) {
      if (severity.getSeverityNumber() == proto.getNumber()) {
        return severity;
      }
    }
    throw new IllegalArgumentException("Unexpected SeverityNumber: " + proto);
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
      traceStateBuilder.put(listMember.substring(0, index), listMember.substring(index + 1));
    }
    return traceStateBuilder.build();
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

  private AgentTestingExporterAccess() {}
}
