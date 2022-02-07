/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.exporter;

import static java.util.concurrent.CompletableFuture.completedFuture;

import com.google.protobuf.InvalidProtocolBufferException;
import com.linecorp.armeria.common.grpc.protocol.ArmeriaStatusException;
import com.linecorp.armeria.server.Server;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.grpc.protocol.AbstractUnaryGrpcService;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogExporter;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceResponse;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.data.LogData;
import io.opentelemetry.sdk.logs.export.LogExporter;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
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

  OtlpInMemoryLogExporter() {
    collector =
        Server.builder()
            .service(
                "/opentelemetry.proto.collector.logs.v1.LogsService/Export",
                new InMemoryOtlpCollector())
            .build();
    collector.start().join();

    delegate =
        OtlpGrpcLogExporter.builder()
            .setEndpoint("http://localhost:" + collector.activeLocalPort())
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
    collector.stop();
    return delegate.shutdown();
  }

  private final class InMemoryOtlpCollector extends AbstractUnaryGrpcService {

    private final byte[] response = ExportLogsServiceResponse.getDefaultInstance().toByteArray();

    @Override
    protected CompletionStage<byte[]> handleMessage(ServiceRequestContext ctx, byte[] message) {
      try {
        collectedRequests.add(ExportLogsServiceRequest.parseFrom(message));
      } catch (InvalidProtocolBufferException e) {
        throw new ArmeriaStatusException(3, e.getMessage(), e);
      }
      return completedFuture(response);
    }
  }
}
