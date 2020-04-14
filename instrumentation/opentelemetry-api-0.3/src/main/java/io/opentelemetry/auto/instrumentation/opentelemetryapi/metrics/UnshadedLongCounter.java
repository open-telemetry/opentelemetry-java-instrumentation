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
import unshaded.io.opentelemetry.metrics.LongCounter;

class UnshadedLongCounter implements LongCounter {

  private final io.opentelemetry.metrics.LongCounter shadedLongCounter;

  UnshadedLongCounter(final io.opentelemetry.metrics.LongCounter shadedLongCounter) {
    this.shadedLongCounter = shadedLongCounter;
  }

  io.opentelemetry.metrics.LongCounter getShadedLongCounter() {
    return shadedLongCounter;
  }

  @Override
  public void add(final long delta, final String... labelKeyValuePairs) {
    shadedLongCounter.add(delta, labelKeyValuePairs);
  }

  @Override
  public BoundLongCounter bind(final String... labelKeyValuePairs) {
    return new BoundInstrument(shadedLongCounter.bind(labelKeyValuePairs));
  }

  class BoundInstrument implements LongCounter.BoundLongCounter {

    private final io.opentelemetry.metrics.LongCounter.BoundLongCounter shadedBoundLongCounter;

    BoundInstrument(
        final io.opentelemetry.metrics.LongCounter.BoundLongCounter shadedBoundLongCounter) {
      this.shadedBoundLongCounter = shadedBoundLongCounter;
    }

    @Override
    public void add(final long delta) {
      shadedBoundLongCounter.add(delta);
    }

    @Override
    public void unbind() {
      shadedBoundLongCounter.unbind();
    }
  }

  static class Builder implements LongCounter.Builder {

    private final io.opentelemetry.metrics.LongCounter.Builder shadedBuilder;

    Builder(final io.opentelemetry.metrics.LongCounter.Builder shadedBuilder) {
      this.shadedBuilder = shadedBuilder;
    }

    @Override
    public LongCounter.Builder setDescription(final String description) {
      shadedBuilder.setDescription(description);
      return this;
    }

    @Override
    public LongCounter.Builder setUnit(final String unit) {
      shadedBuilder.setUnit(unit);
      return this;
    }

    @Override
    public LongCounter.Builder setLabelKeys(final List<String> labelKeys) {
      shadedBuilder.setLabelKeys(labelKeys);
      return this;
    }

    @Override
    public LongCounter.Builder setConstantLabels(final Map<String, String> constantLabels) {
      shadedBuilder.setConstantLabels(constantLabels);
      return this;
    }

    @Override
    public LongCounter.Builder setMonotonic(final boolean monotonic) {
      shadedBuilder.setMonotonic(monotonic);
      return this;
    }

    @Override
    public LongCounter build() {
      return new UnshadedLongCounter(shadedBuilder.build());
    }
  }
}
