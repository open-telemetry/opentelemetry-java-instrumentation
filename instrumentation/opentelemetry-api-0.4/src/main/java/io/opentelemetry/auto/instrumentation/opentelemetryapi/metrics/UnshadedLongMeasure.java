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
import unshaded.io.opentelemetry.metrics.LongMeasure;

class UnshadedLongMeasure implements LongMeasure {

  private final io.opentelemetry.metrics.LongMeasure shadedLongMeasure;

  UnshadedLongMeasure(final io.opentelemetry.metrics.LongMeasure shadedLongMeasure) {
    this.shadedLongMeasure = shadedLongMeasure;
  }

  io.opentelemetry.metrics.LongMeasure getShadedLongMeasure() {
    return shadedLongMeasure;
  }

  @Override
  public void record(final long delta, final String... labelKeyValuePairs) {
    shadedLongMeasure.record(delta, labelKeyValuePairs);
  }

  @Override
  public BoundLongMeasure bind(final String... labelKeyValuePairs) {
    return new BoundInstrument(shadedLongMeasure.bind(labelKeyValuePairs));
  }

  class BoundInstrument implements LongMeasure.BoundLongMeasure {

    private final io.opentelemetry.metrics.LongMeasure.BoundLongMeasure shadedBoundLongMeasure;

    BoundInstrument(
        final io.opentelemetry.metrics.LongMeasure.BoundLongMeasure shadedBoundLongMeasure) {
      this.shadedBoundLongMeasure = shadedBoundLongMeasure;
    }

    @Override
    public void record(final long delta) {
      shadedBoundLongMeasure.record(delta);
    }

    @Override
    public void unbind() {
      shadedBoundLongMeasure.unbind();
    }
  }

  static class Builder implements LongMeasure.Builder {

    private final io.opentelemetry.metrics.LongMeasure.Builder shadedBuilder;

    Builder(final io.opentelemetry.metrics.LongMeasure.Builder shadedBuilder) {
      this.shadedBuilder = shadedBuilder;
    }

    @Override
    public LongMeasure.Builder setDescription(final String description) {
      shadedBuilder.setDescription(description);
      return this;
    }

    @Override
    public LongMeasure.Builder setUnit(final String unit) {
      shadedBuilder.setUnit(unit);
      return this;
    }

    @Override
    public LongMeasure.Builder setConstantLabels(final Map<String, String> constantLabels) {
      shadedBuilder.setConstantLabels(constantLabels);
      return this;
    }

    @Override
    public LongMeasure.Builder setAbsolute(final boolean absolute) {
      shadedBuilder.setAbsolute(absolute);
      return this;
    }

    @Override
    public LongMeasure build() {
      return new UnshadedLongMeasure(shadedBuilder.build());
    }
  }
}
