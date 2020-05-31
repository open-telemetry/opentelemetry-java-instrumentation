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
import unshaded.io.opentelemetry.metrics.DoubleValueRecorder;

class UnshadedDoubleValueRecorder implements DoubleValueRecorder {

  private final io.opentelemetry.metrics.DoubleValueRecorder shadedDoubleValueRecorder;

  protected UnshadedDoubleValueRecorder(final io.opentelemetry.metrics.DoubleValueRecorder shadedDoubleValueRecorder) {
    this.shadedDoubleValueRecorder = shadedDoubleValueRecorder;
  }

  protected io.opentelemetry.metrics.DoubleValueRecorder getShadedDoubleValueRecorder() {
    return shadedDoubleValueRecorder;
  }

  @Override
  public void record(final double delta, final String... labelKeyValuePairs) {
    shadedDoubleValueRecorder.record(delta, labelKeyValuePairs);
  }

  @Override
  public BoundDoubleValueRecorder bind(final String... labelKeyValuePairs) {
    return new BoundInstrument(shadedDoubleValueRecorder.bind(labelKeyValuePairs));
  }

  static class BoundInstrument implements DoubleValueRecorder.BoundDoubleValueRecorder {

    private final io.opentelemetry.metrics.DoubleValueRecorder.BoundDoubleValueRecorder
        shadedBoundDoubleMeasure;

    public BoundInstrument(
        final io.opentelemetry.metrics.DoubleValueRecorder.BoundDoubleValueRecorder shadedBoundDoubleMeasure) {
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

  static class Builder implements DoubleValueRecorder.Builder {

    private final io.opentelemetry.metrics.DoubleValueRecorder.Builder shadedBuilder;

    public Builder(final io.opentelemetry.metrics.DoubleValueRecorder.Builder shadedBuilder) {
      this.shadedBuilder = shadedBuilder;
    }

    @Override
    public DoubleValueRecorder.Builder setDescription(final String description) {
      shadedBuilder.setDescription(description);
      return this;
    }

    @Override
    public DoubleValueRecorder.Builder setUnit(final String unit) {
      shadedBuilder.setUnit(unit);
      return this;
    }

    @Override
    public DoubleValueRecorder.Builder setConstantLabels(final Map<String, String> constantLabels) {
      shadedBuilder.setConstantLabels(constantLabels);
      return this;
    }

    @Override
    public DoubleValueRecorder build() {
      return new UnshadedDoubleValueRecorder(shadedBuilder.build());
    }
  }
}
