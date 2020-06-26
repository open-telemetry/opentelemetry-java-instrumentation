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
import unshaded.io.opentelemetry.metrics.DoubleSumObserver;

class UnshadedDoubleSumObserver implements DoubleSumObserver {

  private final io.opentelemetry.metrics.DoubleSumObserver shadedDoubleSumObserver;

  protected UnshadedDoubleSumObserver(
      final io.opentelemetry.metrics.DoubleSumObserver shadedDoubleSumObserver) {
    this.shadedDoubleSumObserver = shadedDoubleSumObserver;
  }

  @Override
  public void setCallback(final Callback<ResultDoubleSumObserver> metricUpdater) {
    shadedDoubleSumObserver.setCallback(new ShadedResultDoubleSumObserver(metricUpdater));
  }

  static class ShadedResultDoubleSumObserver
      implements AsynchronousInstrument.Callback<
          io.opentelemetry.metrics.DoubleSumObserver.ResultDoubleSumObserver> {

    private final Callback<ResultDoubleSumObserver> metricUpdater;

    protected ShadedResultDoubleSumObserver(final Callback<ResultDoubleSumObserver> metricUpdater) {
      this.metricUpdater = metricUpdater;
    }

    @Override
    public void update(
        final io.opentelemetry.metrics.DoubleSumObserver.ResultDoubleSumObserver result) {
      metricUpdater.update(new UnshadedResultDoubleSumObserver(result));
    }
  }

  static class UnshadedResultDoubleSumObserver implements ResultDoubleSumObserver {

    private final io.opentelemetry.metrics.DoubleSumObserver.ResultDoubleSumObserver
        shadedResultDoubleSumObserver;

    public UnshadedResultDoubleSumObserver(
        final io.opentelemetry.metrics.DoubleSumObserver.ResultDoubleSumObserver
            shadedResultDoubleSumObserver) {
      this.shadedResultDoubleSumObserver = shadedResultDoubleSumObserver;
    }

    @Override
    public void observe(final double value, final String... keyValueLabelPairs) {
      shadedResultDoubleSumObserver.observe(value, keyValueLabelPairs);
    }
  }

  static class Builder implements DoubleSumObserver.Builder {

    private final io.opentelemetry.metrics.DoubleSumObserver.Builder shadedBuilder;

    protected Builder(final io.opentelemetry.metrics.DoubleSumObserver.Builder shadedBuilder) {
      this.shadedBuilder = shadedBuilder;
    }

    @Override
    public DoubleSumObserver.Builder setDescription(final String description) {
      shadedBuilder.setDescription(description);
      return this;
    }

    @Override
    public DoubleSumObserver.Builder setUnit(final String unit) {
      shadedBuilder.setUnit(unit);
      return this;
    }

    @Override
    public DoubleSumObserver.Builder setConstantLabels(final Map<String, String> constantLabels) {
      shadedBuilder.setConstantLabels(constantLabels);
      return this;
    }

    @Override
    public DoubleSumObserver build() {
      return new UnshadedDoubleSumObserver(shadedBuilder.build());
    }
  }
}
