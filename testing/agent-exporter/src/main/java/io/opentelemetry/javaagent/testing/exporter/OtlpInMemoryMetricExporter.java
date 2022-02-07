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
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceResponse;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class OtlpInMemoryMetricExporter implements MetricExporter {

  private static final Logger logger = LoggerFactory.getLogger(OtlpInMemoryMetricExporter.class);

  private final BlockingQueue<ExportMetricsServiceRequest> collectedRequests =
      new LinkedBlockingQueue<>();

  List<byte[]> getCollectedExportRequests() {
    return collectedRequests.stream()
        .map(ExportMetricsServiceRequest::toByteArray)
        .collect(Collectors.toList());
  }

  void reset() {
    delegate.flush().join(1, TimeUnit.SECONDS);
    collectedRequests.clear();
  }

  private final Server collector;
  private final MetricExporter delegate;

  OtlpInMemoryMetricExporter() {
    collector =
        Server.builder()
            .service(
                "/opentelemetry.proto.collector.metrics.v1.MetricsService/Export",
                new InMemoryOtlpCollector())
            .build();
    collector.start().join();

    delegate =
        OtlpGrpcMetricExporter.builder()
            .setEndpoint("http://localhost:" + collector.activeLocalPort())
            .build();
  }

  @Override
  public CompletableResultCode export(Collection<MetricData> metrics) {
    return delegate.export(metrics);
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

    private final byte[] response = ExportMetricsServiceResponse.getDefaultInstance().toByteArray();

    @Override
    protected CompletionStage<byte[]> handleMessage(ServiceRequestContext ctx, byte[] message) {
      try {
        collectedRequests.add(ExportMetricsServiceRequest.parseFrom(message));
      } catch (InvalidProtocolBufferException e) {
        throw new ArmeriaStatusException(3, e.getMessage(), e);
      }
      return completedFuture(response);
    }
  }
}
