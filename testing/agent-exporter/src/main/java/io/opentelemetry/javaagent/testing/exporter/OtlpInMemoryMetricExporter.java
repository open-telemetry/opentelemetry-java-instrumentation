/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.exporter;

import static java.util.logging.Level.CONFIG;

import io.opentelemetry.exporter.internal.otlp.metrics.MetricsRequestMarshaler;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

class OtlpInMemoryMetricExporter implements MetricExporter {

  private static final Logger logger = Logger.getLogger(OtlpInMemoryMetricExporter.class.getName());

  private final Queue<byte[]> collectedRequests = new ConcurrentLinkedQueue<>();

  private static final AggregationTemporality aggregationTemporality = initAggregationTemporality();

  private static AggregationTemporality initAggregationTemporality() {
    // this configuration setting is for external users
    // see https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/7902
    String temporalityProperty = System.getProperty("otel.javaagent.testing.exporter.temporality");
    AggregationTemporality aggregationTemporality;
    if (temporalityProperty == null) {
      aggregationTemporality = AggregationTemporality.DELTA;
    } else {
      aggregationTemporality =
          AggregationTemporality.valueOf(temporalityProperty.toUpperCase(Locale.ROOT));
    }
    logger.log(CONFIG, "Setting aggregation temporality to {0}", aggregationTemporality.toString());
    return aggregationTemporality;
  }

  List<byte[]> getCollectedExportRequests() {
    return new ArrayList<>(collectedRequests);
  }

  void reset() {
    collectedRequests.clear();
  }

  @Override
  public AggregationTemporality getAggregationTemporality(InstrumentType instrumentType) {
    return aggregationTemporality;
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
