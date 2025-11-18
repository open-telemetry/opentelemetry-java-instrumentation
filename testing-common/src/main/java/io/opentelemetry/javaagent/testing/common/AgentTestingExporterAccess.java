/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.common;

import static java.util.stream.Collectors.toList;

import io.opentelemetry.instrumentation.testing.internal.TelemetryConverter;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.testing.internal.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.testing.internal.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.testing.internal.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.testing.internal.proto.trace.v1.ResourceSpans;
import io.opentelemetry.testing.internal.protobuf.InvalidProtocolBufferException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;

public final class AgentTestingExporterAccess {

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

  public static List<SpanData> getExportedSpans() {
    try {
      @SuppressWarnings("unchecked") // casting MethodHandle.invokeExact result
      List<byte[]> bytes = (List<byte[]>) getSpanExportRequests.invokeExact();
      List<ResourceSpans> allResourceSpans =
          bytes.stream()
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

      return TelemetryConverter.getSpanData(allResourceSpans);
    } catch (Throwable t) {
      throw new AssertionError("Could not invoke getSpanExportRequests", t);
    }
  }

  public static List<MetricData> getExportedMetrics() {
    try {
      @SuppressWarnings("unchecked") // casting MethodHandle.invokeExact result
      List<byte[]> bytes = (List<byte[]>) getMetricExportRequests.invokeExact();
      return TelemetryConverter.getMetricsData(
          bytes.stream()
              .map(
                  serialized -> {
                    try {
                      return ExportMetricsServiceRequest.parseFrom(serialized);
                    } catch (InvalidProtocolBufferException e) {
                      throw new AssertionError(e);
                    }
                  })
              .flatMap(request -> request.getResourceMetricsList().stream())
              .collect(toList()));
    } catch (Throwable t) {
      throw new AssertionError("Could not invoke getSpanExportRequests", t);
    }
  }

  public static List<LogRecordData> getExportedLogRecords() {
    try {
      @SuppressWarnings("unchecked") // casting MethodHandle.invokeExact result
      List<byte[]> bytes = (List<byte[]>) getLogExportRequests.invokeExact();
      return TelemetryConverter.getLogRecordData(
          bytes.stream()
              .map(
                  serialized -> {
                    try {
                      return ExportLogsServiceRequest.parseFrom(serialized);
                    } catch (InvalidProtocolBufferException e) {
                      throw new AssertionError(e);
                    }
                  })
              .flatMap(request -> request.getResourceLogsList().stream())
              .collect(toList()));
    } catch (Throwable t) {
      throw new AssertionError("Could not invoke getLogExportRequests", t);
    }
  }

  private AgentTestingExporterAccess() {}
}
