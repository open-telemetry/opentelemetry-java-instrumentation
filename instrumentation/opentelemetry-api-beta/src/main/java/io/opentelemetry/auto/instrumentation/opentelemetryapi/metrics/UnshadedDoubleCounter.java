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
import unshaded.io.opentelemetry.metrics.DoubleCounter;

class UnshadedDoubleCounter implements DoubleCounter {

  private final io.opentelemetry.metrics.DoubleCounter shadedDoubleCounter;

  UnshadedDoubleCounter(final io.opentelemetry.metrics.DoubleCounter shadedDoubleCounter) {
    this.shadedDoubleCounter = shadedDoubleCounter;
  }

  io.opentelemetry.metrics.DoubleCounter getShadedDoubleCounter() {
    return shadedDoubleCounter;
  }

  @Override
  public void add(final double delta, final Labels labels) {
    shadedDoubleCounter.add(delta, LabelsShader.shade(labels));
  }

  @Override
  public BoundDoubleCounter bind(final Labels labels) {
    return new BoundInstrument(shadedDoubleCounter.bind(LabelsShader.shade(labels)));
  }

  static class BoundInstrument implements DoubleCounter.BoundDoubleCounter {

    private final io.opentelemetry.metrics.DoubleCounter.BoundDoubleCounter
        shadedBoundDoubleCounter;

    BoundInstrument(
        final io.opentelemetry.metrics.DoubleCounter.BoundDoubleCounter shadedBoundDoubleCounter) {
      this.shadedBoundDoubleCounter = shadedBoundDoubleCounter;
    }

    @Override
    public void add(final double delta) {
      shadedBoundDoubleCounter.add(delta);
    }

    @Override
    public void unbind() {
      shadedBoundDoubleCounter.unbind();
    }
  }

  static class Builder implements DoubleCounter.Builder {

    private final io.opentelemetry.metrics.DoubleCounter.Builder shadedBuilder;

    Builder(final io.opentelemetry.metrics.DoubleCounter.Builder shadedBuilder) {
      this.shadedBuilder = shadedBuilder;
    }

    @Override
    public DoubleCounter.Builder setDescription(final String description) {
      shadedBuilder.setDescription(description);
      return this;
    }

    @Override
    public DoubleCounter.Builder setUnit(final String unit) {
      shadedBuilder.setUnit(unit);
      return this;
    }

    @Override
    public DoubleCounter.Builder setConstantLabels(final Labels constantLabels) {
      shadedBuilder.setConstantLabels(LabelsShader.shade(constantLabels));
      return this;
    }

    @Override
    public DoubleCounter build() {
      return new UnshadedDoubleCounter(shadedBuilder.build());
    }
  }
}
