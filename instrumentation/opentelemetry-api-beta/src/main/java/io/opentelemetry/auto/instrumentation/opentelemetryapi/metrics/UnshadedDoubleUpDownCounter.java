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

import java.util.Map;
import unshaded.io.opentelemetry.metrics.DoubleUpDownCounter;

class UnshadedDoubleUpDownCounter implements DoubleUpDownCounter {

  private final io.opentelemetry.metrics.DoubleUpDownCounter shadedDoubleUpDownCounter;

  UnshadedDoubleUpDownCounter(
      final io.opentelemetry.metrics.DoubleUpDownCounter shadedDoubleUpDownCounter) {
    this.shadedDoubleUpDownCounter = shadedDoubleUpDownCounter;
  }

  io.opentelemetry.metrics.DoubleUpDownCounter getShadedDoubleUpDownCounter() {
    return shadedDoubleUpDownCounter;
  }

  @Override
  public void add(final double delta, final String... labelKeyValuePairs) {
    shadedDoubleUpDownCounter.add(delta, labelKeyValuePairs);
  }

  @Override
  public BoundDoubleUpDownCounter bind(final String... labelKeyValuePairs) {
    return new BoundInstrument(shadedDoubleUpDownCounter.bind(labelKeyValuePairs));
  }

  static class BoundInstrument implements BoundDoubleUpDownCounter {

    private final io.opentelemetry.metrics.DoubleUpDownCounter.BoundDoubleUpDownCounter
        shadedBoundDoubleUpDownCounter;

    BoundInstrument(
        final io.opentelemetry.metrics.DoubleUpDownCounter.BoundDoubleUpDownCounter
            shadedBoundDoubleUpDownCounter) {
      this.shadedBoundDoubleUpDownCounter = shadedBoundDoubleUpDownCounter;
    }

    @Override
    public void add(final double delta) {
      shadedBoundDoubleUpDownCounter.add(delta);
    }

    @Override
    public void unbind() {
      shadedBoundDoubleUpDownCounter.unbind();
    }
  }

  static class Builder implements DoubleUpDownCounter.Builder {

    private final io.opentelemetry.metrics.DoubleUpDownCounter.Builder shadedBuilder;

    Builder(final io.opentelemetry.metrics.DoubleUpDownCounter.Builder shadedBuilder) {
      this.shadedBuilder = shadedBuilder;
    }

    @Override
    public DoubleUpDownCounter.Builder setDescription(final String description) {
      shadedBuilder.setDescription(description);
      return this;
    }

    @Override
    public DoubleUpDownCounter.Builder setUnit(final String unit) {
      shadedBuilder.setUnit(unit);
      return this;
    }

    @Override
    public DoubleUpDownCounter.Builder setConstantLabels(final Map<String, String> constantLabels) {
      shadedBuilder.setConstantLabels(constantLabels);
      return this;
    }

    @Override
    public DoubleUpDownCounter build() {
      return new UnshadedDoubleUpDownCounter(shadedBuilder.build());
    }
  }
}
