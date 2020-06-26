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

package io.opentelemetry.auto.instrumentation.opentelemetryapi.metrics;

import io.opentelemetry.metrics.AsynchronousInstrument;
import java.util.Map;
import unshaded.io.opentelemetry.metrics.LongUpDownSumObserver;

class UnshadedLongUpDownSumObserver implements LongUpDownSumObserver {

  private final io.opentelemetry.metrics.LongUpDownSumObserver shadedLongUpDownSumObserver;

  protected UnshadedLongUpDownSumObserver(
      final io.opentelemetry.metrics.LongUpDownSumObserver shadedLongUpDownSumObserver) {
    this.shadedLongUpDownSumObserver = shadedLongUpDownSumObserver;
  }

  @Override
  public void setCallback(final Callback<ResultLongUpDownSumObserver> metricUpdater) {
    shadedLongUpDownSumObserver.setCallback(new ShadedResultLongUpDownSumObserver(metricUpdater));
  }

  static class ShadedResultLongUpDownSumObserver
      implements AsynchronousInstrument.Callback<
          io.opentelemetry.metrics.LongUpDownSumObserver.ResultLongUpDownSumObserver> {

    private final Callback<ResultLongUpDownSumObserver> metricUpdater;

    protected ShadedResultLongUpDownSumObserver(
        final Callback<ResultLongUpDownSumObserver> metricUpdater) {
      this.metricUpdater = metricUpdater;
    }

    @Override
    public void update(
        final io.opentelemetry.metrics.LongUpDownSumObserver.ResultLongUpDownSumObserver result) {
      metricUpdater.update(new UnshadedResultLongUpDownSumObserver(result));
    }
  }

  static class UnshadedResultLongUpDownSumObserver implements ResultLongUpDownSumObserver {

    private final io.opentelemetry.metrics.LongUpDownSumObserver.ResultLongUpDownSumObserver
        shadedResultLongUpDownSumObserver;

    public UnshadedResultLongUpDownSumObserver(
        final io.opentelemetry.metrics.LongUpDownSumObserver.ResultLongUpDownSumObserver
            shadedResultLongUpDownSumObserver) {
      this.shadedResultLongUpDownSumObserver = shadedResultLongUpDownSumObserver;
    }

    @Override
    public void observe(final long value, final String... keyValueLabelPairs) {
      shadedResultLongUpDownSumObserver.observe(value, keyValueLabelPairs);
    }
  }

  static class Builder implements LongUpDownSumObserver.Builder {

    private final io.opentelemetry.metrics.LongUpDownSumObserver.Builder shadedBuilder;

    protected Builder(final io.opentelemetry.metrics.LongUpDownSumObserver.Builder shadedBuilder) {
      this.shadedBuilder = shadedBuilder;
    }

    @Override
    public LongUpDownSumObserver.Builder setDescription(final String description) {
      shadedBuilder.setDescription(description);
      return this;
    }

    @Override
    public LongUpDownSumObserver.Builder setUnit(final String unit) {
      shadedBuilder.setUnit(unit);
      return this;
    }

    @Override
    public LongUpDownSumObserver.Builder setConstantLabels(
        final Map<String, String> constantLabels) {
      shadedBuilder.setConstantLabels(constantLabels);
      return this;
    }

    @Override
    public LongUpDownSumObserver build() {
      return new UnshadedLongUpDownSumObserver(shadedBuilder.build());
    }
  }
}
