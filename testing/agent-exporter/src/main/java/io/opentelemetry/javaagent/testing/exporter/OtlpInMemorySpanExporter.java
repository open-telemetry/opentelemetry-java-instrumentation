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
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceResponse;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class OtlpInMemorySpanExporter implements SpanExporter {

  private static final Logger logger = LoggerFactory.getLogger(OtlpInMemorySpanExporter.class);

  private final BlockingQueue<ExportTraceServiceRequest> collectedRequests =
      new LinkedBlockingQueue<>();

  List<byte[]> getCollectedExportRequests() {
    return collectedRequests.stream()
        .map(ExportTraceServiceRequest::toByteArray)
        .collect(Collectors.toList());
  }

  void reset() {
    delegate.flush().join(1, TimeUnit.SECONDS);
    collectedRequests.clear();
  }

  private final Server collector;
  private final SpanExporter delegate;

  OtlpInMemorySpanExporter() {
    collector =
        Server.builder()
            .service(
                "/opentelemetry.proto.collector.trace.v1.TraceService/Export",
                new InMemoryOtlpCollector())
            .build();
    collector.start().join();

    delegate =
        OtlpGrpcSpanExporter.builder()
            .setEndpoint("http://localhost:" + collector.activeLocalPort())
            .build();
  }

  @Override
  public CompletableResultCode export(Collection<SpanData> spans) {
    for (SpanData span : spans) {
      logger.info("Exporting span {}", span);
    }
    return delegate.export(spans);
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

    private final byte[] response = ExportTraceServiceResponse.getDefaultInstance().toByteArray();

    @Override
    protected CompletionStage<byte[]> handleMessage(ServiceRequestContext ctx, byte[] message) {
      try {
        collectedRequests.add(ExportTraceServiceRequest.parseFrom(message));
      } catch (InvalidProtocolBufferException e) {
        throw new ArmeriaStatusException(3, e.getMessage(), e);
      }
      return completedFuture(response);
    }
  }
}
