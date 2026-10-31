/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.internal;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.AggregationTemporalitySelector;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricExporter;
import java.util.Collection;
import java.util.List;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class DeltaPreferredInMemoryMetricExporter implements MetricExporter {

  private final InMemoryMetricExporter delegate;

  public static DeltaPreferredInMemoryMetricExporter create() {
    return new DeltaPreferredInMemoryMetricExporter();
  }

  private DeltaPreferredInMemoryMetricExporter() {
    delegate = InMemoryMetricExporter.create(AggregationTemporality.DELTA);
  }

  @Override
  public AggregationTemporality getAggregationTemporality(InstrumentType instrumentType) {
    try {
      return AggregationTemporalitySelector.deltaPreferred()
          .getAggregationTemporality(instrumentType);
    } catch (NoClassDefFoundError | NoSuchMethodError ignored) {
      // The SDK selector is not available in some supported test SDK versions.
      if (instrumentType == InstrumentType.UP_DOWN_COUNTER
          || instrumentType == InstrumentType.OBSERVABLE_UP_DOWN_COUNTER) {
        return AggregationTemporality.CUMULATIVE;
      }
      return AggregationTemporality.DELTA;
    }
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
    return delegate.shutdown();
  }

  public List<MetricData> getFinishedMetricItems() {
    return delegate.getFinishedMetricItems();
  }

  public void reset() {
    delegate.reset();
  }
}
