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

import java.util.List;
import java.util.Map;
import unshaded.io.opentelemetry.metrics.DoubleObserver;

class UnshadedDoubleObserver implements DoubleObserver {

  private final io.opentelemetry.metrics.DoubleObserver shadedDoubleObserver;

  UnshadedDoubleObserver(final io.opentelemetry.metrics.DoubleObserver shadedDoubleObserver) {
    this.shadedDoubleObserver = shadedDoubleObserver;
  }

  @Override
  public void setCallback(final Callback<ResultDoubleObserver> metricUpdater) {
    shadedDoubleObserver.setCallback(new ShadedResultDoubleObserver(metricUpdater));
  }

  private static class ShadedResultDoubleObserver
      implements io.opentelemetry.metrics.Observer.Callback<
          io.opentelemetry.metrics.DoubleObserver.ResultDoubleObserver> {

    private final Callback<ResultDoubleObserver> metricUpdater;

    ShadedResultDoubleObserver(final Callback<ResultDoubleObserver> metricUpdater) {
      this.metricUpdater = metricUpdater;
    }

    @Override
    public void update(final io.opentelemetry.metrics.DoubleObserver.ResultDoubleObserver result) {
      metricUpdater.update(new UnshadedResultDoubleObserver(result));
    }
  }

  private static class UnshadedResultDoubleObserver implements ResultDoubleObserver {

    private final io.opentelemetry.metrics.DoubleObserver.ResultDoubleObserver
        shadedResultDoubleObserver;

    UnshadedResultDoubleObserver(
        final io.opentelemetry.metrics.DoubleObserver.ResultDoubleObserver
            shadedResultDoubleObserver) {
      this.shadedResultDoubleObserver = shadedResultDoubleObserver;
    }

    @Override
    public void observe(final double value, final String... keyValueLabelPairs) {
      shadedResultDoubleObserver.observe(value, keyValueLabelPairs);
    }
  }

  static class Builder implements DoubleObserver.Builder {

    private final io.opentelemetry.metrics.DoubleObserver.Builder shadedBuilder;

    Builder(final io.opentelemetry.metrics.DoubleObserver.Builder shadedBuilder) {
      this.shadedBuilder = shadedBuilder;
    }

    @Override
    public DoubleObserver.Builder setDescription(final String description) {
      shadedBuilder.setDescription(description);
      return this;
    }

    @Override
    public DoubleObserver.Builder setUnit(final String unit) {
      shadedBuilder.setUnit(unit);
      return this;
    }

    @Override
    public DoubleObserver.Builder setLabelKeys(final List<String> labelKeys) {
      shadedBuilder.setLabelKeys(labelKeys);
      return this;
    }

    @Override
    public DoubleObserver.Builder setConstantLabels(final Map<String, String> constantLabels) {
      shadedBuilder.setConstantLabels(constantLabels);
      return this;
    }

    @Override
    public DoubleObserver.Builder setMonotonic(final boolean monotonic) {
      shadedBuilder.setMonotonic(monotonic);
      return this;
    }

    @Override
    public DoubleObserver build() {
      return new UnshadedDoubleObserver(shadedBuilder.build());
    }
  }
}
