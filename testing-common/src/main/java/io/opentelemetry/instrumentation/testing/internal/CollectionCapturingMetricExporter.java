/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.internal;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Captures metric collections separately so tests can assert snapshot and delta semantics.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class CollectionCapturingMetricExporter implements MetricExporter {

  private final ConcurrentLinkedQueue<List<MetricData>> collections = new ConcurrentLinkedQueue<>();

  @Override
  public AggregationTemporality getAggregationTemporality(InstrumentType instrumentType) {
    return AggregationTemporality.DELTA;
  }

  @Override
  public CompletableResultCode export(Collection<MetricData> metrics) {
    collections.add(Collections.unmodifiableList(new ArrayList<>(metrics)));
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

  public List<List<MetricData>> getCollections() {
    return new ArrayList<>(collections);
  }

  public List<MetricData> getFinishedMetricItems() {
    List<MetricData> metrics = new ArrayList<>();
    for (List<MetricData> collection : collections) {
      metrics.addAll(collection);
    }
    return metrics;
  }

  public void reset() {
    collections.clear();
  }
}
