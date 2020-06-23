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
import unshaded.io.opentelemetry.metrics.DoubleValueObserver;

class UnshadedDoubleValueObserver implements DoubleValueObserver {

  private final io.opentelemetry.metrics.DoubleValueObserver shadedDoubleValueObserver;

  protected UnshadedDoubleValueObserver(
      final io.opentelemetry.metrics.DoubleValueObserver shadedDoubleValueObserver) {
    this.shadedDoubleValueObserver = shadedDoubleValueObserver;
  }

  @Override
  public void setCallback(final Callback<DoubleResult> metricUpdater) {
    shadedDoubleValueObserver.setCallback(new ShadedResultDoubleValueObserver(metricUpdater));
  }

  static class ShadedResultDoubleValueObserver
      implements AsynchronousInstrument.Callback<
          io.opentelemetry.metrics.DoubleValueObserver.DoubleResult> {

    private final Callback<DoubleResult> metricUpdater;

    protected ShadedResultDoubleValueObserver(final Callback<DoubleResult> metricUpdater) {
      this.metricUpdater = metricUpdater;
    }

    @Override
    public void update(final io.opentelemetry.metrics.DoubleValueObserver.DoubleResult result) {
      metricUpdater.update(new UnshadedResultDoubleValueObserver(result));
    }
  }

  static class UnshadedResultDoubleValueObserver implements DoubleResult {

    private final io.opentelemetry.metrics.DoubleValueObserver.DoubleResult
        shadedResultDoubleValueObserver;

    public UnshadedResultDoubleValueObserver(
        final io.opentelemetry.metrics.DoubleValueObserver.DoubleResult
            shadedResultDoubleValueObserver) {
      this.shadedResultDoubleValueObserver = shadedResultDoubleValueObserver;
    }

    @Override
    public void observe(final double value, final Labels labels) {
      shadedResultDoubleValueObserver.observe(value, LabelsShader.shade(labels));
    }
  }

  static class Builder implements DoubleValueObserver.Builder {

    private final io.opentelemetry.metrics.DoubleValueObserver.Builder shadedBuilder;

    protected Builder(final io.opentelemetry.metrics.DoubleValueObserver.Builder shadedBuilder) {
      this.shadedBuilder = shadedBuilder;
    }

    @Override
    public DoubleValueObserver.Builder setDescription(final String description) {
      shadedBuilder.setDescription(description);
      return this;
    }

    @Override
    public DoubleValueObserver.Builder setUnit(final String unit) {
      shadedBuilder.setUnit(unit);
      return this;
    }

    @Override
    public DoubleValueObserver.Builder setConstantLabels(final Labels constantLabels) {
      shadedBuilder.setConstantLabels(LabelsShader.shade(constantLabels));
      return this;
    }

    @Override
    public DoubleValueObserver build() {
      return new UnshadedDoubleValueObserver(shadedBuilder.build());
    }
  }
}
