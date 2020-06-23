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
import unshaded.io.opentelemetry.common.Labels;
import unshaded.io.opentelemetry.metrics.LongValueObserver;

class UnshadedLongValueObserver implements LongValueObserver {

  private final io.opentelemetry.metrics.LongValueObserver shadedLongValueObserver;

  public UnshadedLongValueObserver(
      final io.opentelemetry.metrics.LongValueObserver shadedLongValueObserver) {
    this.shadedLongValueObserver = shadedLongValueObserver;
  }

  @Override
  public void setCallback(final Callback<LongResult> metricUpdater) {
    shadedLongValueObserver.setCallback(new ShadedResultLongValueObserver(metricUpdater));
  }

  public static class ShadedResultLongValueObserver
      implements io.opentelemetry.metrics.AsynchronousInstrument.Callback<
          io.opentelemetry.metrics.LongValueObserver.LongResult> {

    private final Callback<LongResult> metricUpdater;

    public ShadedResultLongValueObserver(final Callback<LongResult> metricUpdater) {
      this.metricUpdater = metricUpdater;
    }

    @Override
    public void update(final io.opentelemetry.metrics.LongValueObserver.LongResult result) {
      metricUpdater.update(new UnshadedResultLongValueObserver(result));
    }
  }

  public static class UnshadedResultLongValueObserver implements LongResult {

    private final io.opentelemetry.metrics.LongValueObserver.LongResult
        shadedResultLongValueObserver;

    public UnshadedResultLongValueObserver(
        final io.opentelemetry.metrics.LongValueObserver.LongResult shadedResultLongValueObserver) {
      this.shadedResultLongValueObserver = shadedResultLongValueObserver;
    }

    @Override
    public void observe(final long value, final Labels labels) {
      shadedResultLongValueObserver.observe(value, LabelsShader.shade(labels));
    }
  }

  static class Builder implements LongValueObserver.Builder {

    private final io.opentelemetry.metrics.LongValueObserver.Builder shadedBuilder;

    public Builder(final io.opentelemetry.metrics.LongValueObserver.Builder shadedBuilder) {
      this.shadedBuilder = shadedBuilder;
    }

    @Override
    public LongValueObserver.Builder setDescription(final String description) {
      shadedBuilder.setDescription(description);
      return this;
    }

    @Override
    public LongValueObserver.Builder setUnit(final String unit) {
      shadedBuilder.setUnit(unit);
      return this;
    }

    @Override
    public LongValueObserver.Builder setConstantLabels(final Labels constantLabels) {
      shadedBuilder.setConstantLabels(LabelsShader.shade(constantLabels));
      return this;
    }

    @Override
    public LongValueObserver build() {
      return new UnshadedLongValueObserver(shadedBuilder.build());
    }
  }
}
