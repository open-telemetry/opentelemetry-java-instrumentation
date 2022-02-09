/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.exporter;

import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogExporter;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceResponse;
import io.opentelemetry.proto.collector.logs.v1.LogsServiceGrpc;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.data.LogData;
import io.opentelemetry.sdk.logs.export.LogExporter;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class OtlpInMemoryLogExporter implements LogExporter {

  private static final Logger logger = LoggerFactory.getLogger(OtlpInMemoryLogExporter.class);

  private final BlockingQueue<ExportLogsServiceRequest> collectedRequests =
      new LinkedBlockingQueue<>();

  List<byte[]> getCollectedExportRequests() {
    return collectedRequests.stream()
        .map(ExportLogsServiceRequest::toByteArray)
        .collect(Collectors.toList());
  }

  void reset() {
    delegate.flush().join(1, TimeUnit.SECONDS);
    collectedRequests.clear();
  }

  private final Server collector;
  private final LogExporter delegate;

  // TODO(anuraaga): https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/5314
  @SuppressWarnings("deprecation")
  OtlpInMemoryLogExporter() {
    String serverName = InProcessServerBuilder.generateName();

    collector =
        InProcessServerBuilder.forName(serverName)
            .directExecutor()
            .addService(new InMemoryOtlpCollector())
            .build();
    try {
      collector.start();
    } catch (IOException e) {
      throw new AssertionError("Could not start in-process collector.", e);
    }

    delegate =
        OtlpGrpcLogExporter.builder()
            .setChannel(InProcessChannelBuilder.forName(serverName).directExecutor().build())
            .build();
  }

  @Override
  public CompletableResultCode export(Collection<LogData> logs) {
    for (LogData log : logs) {
      logger.info("Exporting log {}", log);
    }
    return delegate.export(logs);
  }

  @Override
  public CompletableResultCode flush() {
    return delegate.flush();
  }

  @Override
  public CompletableResultCode shutdown() {
    collector.shutdown();
    return delegate.shutdown();
  }

  private class InMemoryOtlpCollector extends LogsServiceGrpc.LogsServiceImplBase {

    @Override
    public void export(
        ExportLogsServiceRequest request,
        StreamObserver<ExportLogsServiceResponse> responseObserver) {
      collectedRequests.add(request);
      responseObserver.onNext(ExportLogsServiceResponse.getDefaultInstance());
      responseObserver.onCompleted();
    }
  }
}
