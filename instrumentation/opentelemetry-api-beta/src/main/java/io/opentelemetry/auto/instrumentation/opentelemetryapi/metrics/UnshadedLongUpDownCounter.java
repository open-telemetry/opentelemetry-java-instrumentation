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
import unshaded.io.opentelemetry.metrics.LongUpDownCounter;

class UnshadedLongUpDownCounter implements LongUpDownCounter {

  private final io.opentelemetry.metrics.LongUpDownCounter shadedLongUpDownCounter;

  UnshadedLongUpDownCounter(
      final io.opentelemetry.metrics.LongUpDownCounter shadedLongUpDownCounter) {
    this.shadedLongUpDownCounter = shadedLongUpDownCounter;
  }

  io.opentelemetry.metrics.LongUpDownCounter getShadedLongUpDownCounter() {
    return shadedLongUpDownCounter;
  }

  @Override
  public void add(final long delta, final String... labelKeyValuePairs) {
    shadedLongUpDownCounter.add(delta, labelKeyValuePairs);
  }

  @Override
  public BoundLongUpDownCounter bind(final String... labelKeyValuePairs) {
    return new BoundInstrument(shadedLongUpDownCounter.bind(labelKeyValuePairs));
  }

  static class BoundInstrument implements BoundLongUpDownCounter {

    private final io.opentelemetry.metrics.LongUpDownCounter.BoundLongUpDownCounter
        shadedBoundLongUpDownCounter;

    BoundInstrument(
        final io.opentelemetry.metrics.LongUpDownCounter.BoundLongUpDownCounter
            shadedBoundLongUpDownCounter) {
      this.shadedBoundLongUpDownCounter = shadedBoundLongUpDownCounter;
    }

    @Override
    public void add(final long delta) {
      shadedBoundLongUpDownCounter.add(delta);
    }

    @Override
    public void unbind() {
      shadedBoundLongUpDownCounter.unbind();
    }
  }

  static class Builder implements LongUpDownCounter.Builder {

    private final io.opentelemetry.metrics.LongUpDownCounter.Builder shadedBuilder;

    Builder(final io.opentelemetry.metrics.LongUpDownCounter.Builder shadedBuilder) {
      this.shadedBuilder = shadedBuilder;
    }

    @Override
    public LongUpDownCounter.Builder setDescription(final String description) {
      shadedBuilder.setDescription(description);
      return this;
    }

    @Override
    public LongUpDownCounter.Builder setUnit(final String unit) {
      shadedBuilder.setUnit(unit);
      return this;
    }

    @Override
    public LongUpDownCounter.Builder setConstantLabels(final Map<String, String> constantLabels) {
      shadedBuilder.setConstantLabels(constantLabels);
      return this;
    }

    @Override
    public LongUpDownCounter build() {
      return new UnshadedLongUpDownCounter(shadedBuilder.build());
    }
  }
}
