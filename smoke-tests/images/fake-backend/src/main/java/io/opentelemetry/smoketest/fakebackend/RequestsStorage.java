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

public class RequestsStorage {

  private final BlockingQueue<ExportTraceServiceRequest> traceExportRequests =
      new LinkedBlockingDeque<>();
  private final BlockingQueue<ExportMetricsServiceRequest> metricsExportRequests =
      new LinkedBlockingDeque<>();
  private final BlockingQueue<ExportLogsServiceRequest> logsExportRequests =
      new LinkedBlockingDeque<>();

  public void addTrace(ExportTraceServiceRequest request) {
    traceExportRequests.add(request);
  }

  public void addMetricsRequest(ExportMetricsServiceRequest request) {
    metricsExportRequests.add(request);
  }

  public void addLogsRequest(ExportLogsServiceRequest request) {
    logsExportRequests.add(request);
  }

  public List<ExportTraceServiceRequest> getTraceRequests() {
    return ImmutableList.copyOf(traceExportRequests);
  }

  public List<ExportMetricsServiceRequest> getMetricsRequests() {
    return ImmutableList.copyOf(metricsExportRequests);
  }

  public List<ExportLogsServiceRequest> getLogsRequests() {
    return ImmutableList.copyOf(logsExportRequests);
  }

  public void clearTraces() {
    traceExportRequests.clear();
  }

  public void clearMetrics() {
    metricsExportRequests.clear();
  }

  public void clearLogs() {
    logsExportRequests.clear();
  }
}
