/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.exporter;

import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.BlockingQueue;
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

  private final OtlpInMemoryCollector collector;
  private final MetricExporter delegate;

  OtlpInMemoryMetricExporter(OtlpInMemoryCollector collector) {
    this.collector = collector;

    collector.start();

    delegate = OtlpGrpcMetricExporter.builder().setEndpoint(collector.getEndpoint()).build();
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
}
