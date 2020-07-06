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

import io.opentelemetry.auto.instrumentation.opentelemetryapi.LabelsShader;
import io.opentelemetry.metrics.AsynchronousInstrument;
import unshaded.io.opentelemetry.common.Labels;
import unshaded.io.opentelemetry.metrics.DoubleUpDownSumObserver;

class UnshadedDoubleUpDownSumObserver implements DoubleUpDownSumObserver {

  private final io.opentelemetry.metrics.DoubleUpDownSumObserver shadedDoubleUpDownSumObserver;

  protected UnshadedDoubleUpDownSumObserver(
      final io.opentelemetry.metrics.DoubleUpDownSumObserver shadedDoubleUpDownSumObserver) {
    this.shadedDoubleUpDownSumObserver = shadedDoubleUpDownSumObserver;
  }

  @Override
  public void setCallback(final Callback<DoubleResult> metricUpdater) {
    shadedDoubleUpDownSumObserver.setCallback(
        new ShadedResultDoubleUpDownSumObserver(metricUpdater));
  }

  static class ShadedResultDoubleUpDownSumObserver
      implements AsynchronousInstrument.Callback<
          io.opentelemetry.metrics.DoubleUpDownSumObserver.DoubleResult> {

    private final Callback<DoubleResult> metricUpdater;

    protected ShadedResultDoubleUpDownSumObserver(final Callback<DoubleResult> metricUpdater) {
      this.metricUpdater = metricUpdater;
    }

    @Override
    public void update(final io.opentelemetry.metrics.DoubleUpDownSumObserver.DoubleResult result) {
      metricUpdater.update(new UnshadedResultDoubleUpDownSumObserver(result));
    }
  }

  static class UnshadedResultDoubleUpDownSumObserver implements DoubleResult {

    private final io.opentelemetry.metrics.DoubleUpDownSumObserver.DoubleResult
        shadedResultDoubleUpDownSumObserver;

    public UnshadedResultDoubleUpDownSumObserver(
        final io.opentelemetry.metrics.DoubleUpDownSumObserver.DoubleResult
            shadedResultDoubleUpDownSumObserver) {
      this.shadedResultDoubleUpDownSumObserver = shadedResultDoubleUpDownSumObserver;
    }

    @Override
    public void observe(final double value, final Labels labels) {
      shadedResultDoubleUpDownSumObserver.observe(value, LabelsShader.shade(labels));
    }
  }

  static class Builder implements DoubleUpDownSumObserver.Builder {

    private final io.opentelemetry.metrics.DoubleUpDownSumObserver.Builder shadedBuilder;

    protected Builder(
        final io.opentelemetry.metrics.DoubleUpDownSumObserver.Builder shadedBuilder) {
      this.shadedBuilder = shadedBuilder;
    }

    @Override
    public DoubleUpDownSumObserver.Builder setDescription(final String description) {
      shadedBuilder.setDescription(description);
      return this;
    }

    @Override
    public DoubleUpDownSumObserver.Builder setUnit(final String unit) {
      shadedBuilder.setUnit(unit);
      return this;
    }

    @Override
    public DoubleUpDownSumObserver.Builder setConstantLabels(final Labels constantLabels) {
      shadedBuilder.setConstantLabels(LabelsShader.shade(constantLabels));
      return this;
    }

    @Override
    public DoubleUpDownSumObserver build() {
      return new UnshadedDoubleUpDownSumObserver(shadedBuilder.build());
    }
  }
}
