/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.common;

import io.opentelemetry.instrumentation.testing.internal.TelemetryConverter;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.trace.data.SpanData;
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

  @SuppressWarnings("unchecked")
  public static List<SpanData> getExportedSpans() {
    try {
      return TelemetryConverter.getSpanData((List<byte[]>) getSpanExportRequests.invokeExact());
    } catch (Throwable t) {
      throw new AssertionError("Could not invoke getSpanExportRequests", t);
    }
  }

  @SuppressWarnings("unchecked")
  public static List<MetricData> getExportedMetrics() {
    try {
      return TelemetryConverter.getMetricsData(
          (List<byte[]>) getMetricExportRequests.invokeExact());
    } catch (Throwable t) {
      throw new AssertionError("Could not invoke getSpanExportRequests", t);
    }
  }

  @SuppressWarnings("unchecked")
  public static List<LogRecordData> getExportedLogRecords() {
    try {
      return TelemetryConverter.getLogRecordData((List<byte[]>) getLogExportRequests.invokeExact());
    } catch (Throwable t) {
      throw new AssertionError("Could not invoke getLogExportRequests", t);
    }
  }

  private AgentTestingExporterAccess() {}
}
