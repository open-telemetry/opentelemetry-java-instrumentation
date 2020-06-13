/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentelemetry.auto.instrumentation.opentelemetryapi.vbeta.metrics;

import io.opentelemetry.metrics.AsynchronousInstrument;
import java.util.Map;
import unshaded.io.opentelemetry.metrics.LongSumObserver;

class UnshadedLongSumObserver implements LongSumObserver {

  private final io.opentelemetry.metrics.LongSumObserver shadedLongSumObserver;

  protected UnshadedLongSumObserver(
      final io.opentelemetry.metrics.LongSumObserver shadedLongSumObserver) {
    this.shadedLongSumObserver = shadedLongSumObserver;
  }

  @Override
  public void setCallback(final Callback<ResultLongSumObserver> metricUpdater) {
    shadedLongSumObserver.setCallback(new ShadedResultLongSumObserver(metricUpdater));
  }

  static class ShadedResultLongSumObserver
      implements AsynchronousInstrument.Callback<
          io.opentelemetry.metrics.LongSumObserver.ResultLongSumObserver> {

    private final Callback<ResultLongSumObserver> metricUpdater;

    protected ShadedResultLongSumObserver(final Callback<ResultLongSumObserver> metricUpdater) {
      this.metricUpdater = metricUpdater;
    }

    @Override
    public void update(
        final io.opentelemetry.metrics.LongSumObserver.ResultLongSumObserver result) {
      metricUpdater.update(new UnshadedResultLongSumObserver(result));
    }
  }

  static class UnshadedResultLongSumObserver implements ResultLongSumObserver {

    private final io.opentelemetry.metrics.LongSumObserver.ResultLongSumObserver
        shadedResultLongSumObserver;

    public UnshadedResultLongSumObserver(
        final io.opentelemetry.metrics.LongSumObserver.ResultLongSumObserver
            shadedResultLongSumObserver) {
      this.shadedResultLongSumObserver = shadedResultLongSumObserver;
    }

    @Override
    public void observe(final long value, final String... keyValueLabelPairs) {
      shadedResultLongSumObserver.observe(value, keyValueLabelPairs);
    }
  }

  static class Builder implements LongSumObserver.Builder {

    private final io.opentelemetry.metrics.LongSumObserver.Builder shadedBuilder;

    protected Builder(final io.opentelemetry.metrics.LongSumObserver.Builder shadedBuilder) {
      this.shadedBuilder = shadedBuilder;
    }

    @Override
    public LongSumObserver.Builder setDescription(final String description) {
      shadedBuilder.setDescription(description);
      return this;
    }

    @Override
    public LongSumObserver.Builder setUnit(final String unit) {
      shadedBuilder.setUnit(unit);
      return this;
    }

    @Override
    public LongSumObserver.Builder setConstantLabels(final Map<String, String> constantLabels) {
      shadedBuilder.setConstantLabels(constantLabels);
      return this;
    }

    @Override
    public LongSumObserver build() {
      return new UnshadedLongSumObserver(shadedBuilder.build());
    }
  }
}
