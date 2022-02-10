/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.exporter;

import io.opentelemetry.exporter.internal.otlp.metrics.MetricsRequestMarshaler;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class OtlpInMemoryMetricExporter implements MetricExporter {

  private static final Logger logger = LoggerFactory.getLogger(OtlpInMemoryMetricExporter.class);

  private final Queue<byte[]> collectedRequests = new ConcurrentLinkedQueue<>();

  List<byte[]> getCollectedExportRequests() {
    return new ArrayList<>(collectedRequests);
  }

  void reset() {
    collectedRequests.clear();
  }

  @Override
  public CompletableResultCode export(Collection<MetricData> metrics) {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try {
      MetricsRequestMarshaler.create(metrics).writeBinaryTo(bos);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    collectedRequests.add(bos.toByteArray());
    return CompletableResultCode.ofSuccess();
  }

  @Override
  public CompletableResultCode flush() {
    return CompletableResultCode.ofSuccess();
  }

  @Override
  public CompletableResultCode shutdown() {
    reset();
    return CompletableResultCode.ofSuccess();
  }
}
