/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.exporter;

import static java.util.concurrent.CompletableFuture.completedFuture;

import com.linecorp.armeria.server.Server;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.grpc.protocol.AbstractUnaryGrpcService;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceResponse;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceResponse;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;

final class OtlpInMemoryCollector {

  private final OtlpService logsService;
  private final OtlpService metricsService;
  private final OtlpService traceService;

  private final Server server;

  // To shutdown after all exporters are shutdown;
  private int refCnt;

  OtlpInMemoryCollector() {
    logsService = new OtlpService(ExportLogsServiceResponse.getDefaultInstance().toByteArray());
    metricsService =
        new OtlpService(ExportMetricsServiceResponse.getDefaultInstance().toByteArray());
    traceService = new OtlpService(ExportTraceServiceResponse.getDefaultInstance().toByteArray());
    server =
        Server.builder()
            .service("/opentelemetry.proto.collector.logs.v1.LogsService/Export", logsService)
            .service(
                "/opentelemetry.proto.collector.metrics.v1.MetricsService/Export", metricsService)
            .service("/opentelemetry.proto.collector.trace.v1.TraceService/Export", traceService)
            .build();
  }

  synchronized void start() {
    if (refCnt == 0) {
      server.start().join();
    }
    refCnt++;
  }

  synchronized void stop() {
    refCnt--;
    if (refCnt == 0) {
      server.stop();
    }
  }

  String getEndpoint() {
    return "http://localhost:" + server.activeLocalPort();
  }

  List<byte[]> getLogsExportRequests() {
    return logsService.getCollectedRequests();
  }

  List<byte[]> getMetricsExportRequests() {
    return metricsService.getCollectedRequests();
  }

  List<byte[]> getTraceExportRequests() {
    return traceService.getCollectedRequests();
  }

  void reset() {
    logsService.reset();
    metricsService.reset();
    traceService.reset();
  }

  private static final class OtlpService extends AbstractUnaryGrpcService {
    private final Queue<byte[]> collectedRequests = new ConcurrentLinkedQueue<>();

    private final byte[] response;

    OtlpService(byte[] response) {
      this.response = response;
    }

    List<byte[]> getCollectedRequests() {
      return new ArrayList<>(collectedRequests);
    }

    void reset() {
      collectedRequests.clear();
    }

    @Override
    protected CompletionStage<byte[]> handleMessage(ServiceRequestContext ctx, byte[] message) {
      collectedRequests.add(message);
      return completedFuture(response);
    }
  }
}
