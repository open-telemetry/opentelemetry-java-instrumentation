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
import unshaded.io.opentelemetry.metrics.DoubleSumObserver;

class UnshadedDoubleSumObserver implements DoubleSumObserver {

  private final io.opentelemetry.metrics.DoubleSumObserver shadedDoubleSumObserver;

  protected UnshadedDoubleSumObserver(
      final io.opentelemetry.metrics.DoubleSumObserver shadedDoubleSumObserver) {
    this.shadedDoubleSumObserver = shadedDoubleSumObserver;
  }

  @Override
  public void setCallback(final Callback<DoubleResult> metricUpdater) {
    shadedDoubleSumObserver.setCallback(new ShadedResultDoubleSumObserver(metricUpdater));
  }

  static class ShadedResultDoubleSumObserver
      implements AsynchronousInstrument.Callback<
          io.opentelemetry.metrics.DoubleSumObserver.DoubleResult> {

    private final Callback<DoubleResult> metricUpdater;

    protected ShadedResultDoubleSumObserver(final Callback<DoubleResult> metricUpdater) {
      this.metricUpdater = metricUpdater;
    }

    @Override
    public void update(final io.opentelemetry.metrics.DoubleSumObserver.DoubleResult result) {
      metricUpdater.update(new UnshadedResultDoubleSumObserver(result));
    }
  }

  static class UnshadedResultDoubleSumObserver implements DoubleResult {

    private final io.opentelemetry.metrics.DoubleSumObserver.DoubleResult
        shadedResultDoubleSumObserver;

    public UnshadedResultDoubleSumObserver(
        final io.opentelemetry.metrics.DoubleSumObserver.DoubleResult
            shadedResultDoubleSumObserver) {
      this.shadedResultDoubleSumObserver = shadedResultDoubleSumObserver;
    }

    @Override
    public void observe(final double value, final Labels labels) {
      shadedResultDoubleSumObserver.observe(value, LabelsShader.shade(labels));
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
    public DoubleSumObserver.Builder setConstantLabels(final Labels constantLabels) {
      shadedBuilder.setConstantLabels(LabelsShader.shade(constantLabels));
      return this;
    }

    @Override
    public DoubleSumObserver build() {
      return new UnshadedDoubleSumObserver(shadedBuilder.build());
    }
  }
}
