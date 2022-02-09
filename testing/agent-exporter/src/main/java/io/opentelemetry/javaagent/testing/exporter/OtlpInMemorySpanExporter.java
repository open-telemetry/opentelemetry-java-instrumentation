/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.exporter;

import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceResponse;
import io.opentelemetry.proto.collector.trace.v1.TraceServiceGrpc;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.BlockingQueue;
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

  // TODO(anuraaga): https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/5314
  @SuppressWarnings("deprecation")
  OtlpInMemorySpanExporter() {
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
        OtlpGrpcSpanExporter.builder()
            .setChannel(InProcessChannelBuilder.forName(serverName).directExecutor().build())
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
    collector.shutdown();
    return delegate.shutdown();
  }

  private class InMemoryOtlpCollector extends TraceServiceGrpc.TraceServiceImplBase {

    @Override
    public void export(
        ExportTraceServiceRequest request,
        StreamObserver<ExportTraceServiceResponse> responseObserver) {
      collectedRequests.add(request);
      responseObserver.onNext(ExportTraceServiceResponse.getDefaultInstance());
      responseObserver.onCompleted();
    }
  }
}
