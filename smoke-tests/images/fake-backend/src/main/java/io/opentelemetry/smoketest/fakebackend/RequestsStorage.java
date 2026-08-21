/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.fakebackend;

import com.google.common.collect.ImmutableList;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

class RequestsStorage {

  private final BlockingQueue<ExportTraceServiceRequest> traceExportRequests =
      new LinkedBlockingDeque<>();
  private final BlockingQueue<ExportMetricsServiceRequest> metricsExportRequests =
      new LinkedBlockingDeque<>();
  private final BlockingQueue<ExportLogsServiceRequest> logsExportRequests =
      new LinkedBlockingDeque<>();

  void addTrace(ExportTraceServiceRequest request) {
    traceExportRequests.add(request);
  }

  void addMetricsRequest(ExportMetricsServiceRequest request) {
    metricsExportRequests.add(request);
  }

  void addLogsRequest(ExportLogsServiceRequest request) {
    logsExportRequests.add(request);
  }

  List<ExportTraceServiceRequest> getTraceRequests() {
    return ImmutableList.copyOf(traceExportRequests);
  }

  List<ExportMetricsServiceRequest> getMetricsRequests() {
    return ImmutableList.copyOf(metricsExportRequests);
  }

  List<ExportLogsServiceRequest> getLogsRequests() {
    return ImmutableList.copyOf(logsExportRequests);
  }

  void clearTraces() {
    traceExportRequests.clear();
  }

  void clearMetrics() {
    metricsExportRequests.clear();
  }

  void clearLogs() {
    logsExportRequests.clear();
  }
}
