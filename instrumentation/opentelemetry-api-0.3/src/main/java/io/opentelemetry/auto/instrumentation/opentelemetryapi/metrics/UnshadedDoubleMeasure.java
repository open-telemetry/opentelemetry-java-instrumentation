/*
 * Copyright 2020, OpenTelemetry Authors
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
import unshaded.io.opentelemetry.metrics.DoubleMeasure;

class UnshadedDoubleMeasure implements DoubleMeasure {

  private final io.opentelemetry.metrics.DoubleMeasure shadedDoubleMeasure;

  UnshadedDoubleMeasure(final io.opentelemetry.metrics.DoubleMeasure shadedDoubleMeasure) {
    this.shadedDoubleMeasure = shadedDoubleMeasure;
  }

  io.opentelemetry.metrics.DoubleMeasure getShadedDoubleMeasure() {
    return shadedDoubleMeasure;
  }

  @Override
  public void record(final double delta, final String... labelKeyValuePairs) {
    shadedDoubleMeasure.record(delta, labelKeyValuePairs);
  }

  @Override
  public BoundDoubleMeasure bind(final String... labelKeyValuePairs) {
    return new BoundInstrument(shadedDoubleMeasure.bind(labelKeyValuePairs));
  }

  static class BoundInstrument implements DoubleMeasure.BoundDoubleMeasure {

    private final io.opentelemetry.metrics.DoubleMeasure.BoundDoubleMeasure
        shadedBoundDoubleMeasure;

    BoundInstrument(
        final io.opentelemetry.metrics.DoubleMeasure.BoundDoubleMeasure shadedBoundDoubleMeasure) {
      this.shadedBoundDoubleMeasure = shadedBoundDoubleMeasure;
    }

    @Override
    public void record(final double delta) {
      shadedBoundDoubleMeasure.record(delta);
    }

    @Override
    public void unbind() {
      shadedBoundDoubleMeasure.unbind();
    }
  }

  static class Builder implements DoubleMeasure.Builder {

    private final io.opentelemetry.metrics.DoubleMeasure.Builder shadedBuilder;

    Builder(final io.opentelemetry.metrics.DoubleMeasure.Builder shadedBuilder) {
      this.shadedBuilder = shadedBuilder;
    }

    @Override
    public DoubleMeasure.Builder setDescription(final String description) {
      shadedBuilder.setDescription(description);
      return this;
    }

    @Override
    public DoubleMeasure.Builder setUnit(final String unit) {
      shadedBuilder.setUnit(unit);
      return this;
    }

    @Override
    public DoubleMeasure.Builder setLabelKeys(final List<String> labelKeys) {
      shadedBuilder.setLabelKeys(labelKeys);
      return this;
    }

    @Override
    public DoubleMeasure.Builder setConstantLabels(final Map<String, String> constantLabels) {
      shadedBuilder.setConstantLabels(constantLabels);
      return this;
    }

    @Override
    public DoubleMeasure.Builder setAbsolute(final boolean absolute) {
      shadedBuilder.setAbsolute(absolute);
      return this;
    }

    @Override
    public DoubleMeasure build() {
      return new UnshadedDoubleMeasure(shadedBuilder.build());
    }
  }
}
