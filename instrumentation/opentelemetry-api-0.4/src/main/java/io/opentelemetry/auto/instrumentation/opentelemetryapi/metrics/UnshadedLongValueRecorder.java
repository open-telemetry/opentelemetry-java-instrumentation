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
import unshaded.io.opentelemetry.metrics.LongValueRecorder;

class UnshadedLongValueRecorder implements LongValueRecorder {

  private final io.opentelemetry.metrics.LongValueRecorder shadedLongValueRecorder;

  protected UnshadedLongValueRecorder(
      final io.opentelemetry.metrics.LongValueRecorder shadedLongValueRecorder) {
    this.shadedLongValueRecorder = shadedLongValueRecorder;
  }

  public io.opentelemetry.metrics.LongValueRecorder getShadedLongValueRecorder() {
    return shadedLongValueRecorder;
  }

  @Override
  public void record(final long delta, final String... labelKeyValuePairs) {
    shadedLongValueRecorder.record(delta, labelKeyValuePairs);
  }

  @Override
  public BoundLongValueRecorder bind(final String... labelKeyValuePairs) {
    return new BoundInstrument(shadedLongValueRecorder.bind(labelKeyValuePairs));
  }

  static class BoundInstrument implements LongValueRecorder.BoundLongValueRecorder {

    private final io.opentelemetry.metrics.LongValueRecorder.BoundLongValueRecorder
        shadedBoundLongValueRecorder;

    protected BoundInstrument(
        final io.opentelemetry.metrics.LongValueRecorder.BoundLongValueRecorder
            shadedBoundLongValueRecorder) {
      this.shadedBoundLongValueRecorder = shadedBoundLongValueRecorder;
    }

    @Override
    public void record(final long delta) {
      shadedBoundLongValueRecorder.record(delta);
    }

    @Override
    public void unbind() {
      shadedBoundLongValueRecorder.unbind();
    }
  }

  static class Builder implements LongValueRecorder.Builder {

    private final io.opentelemetry.metrics.LongValueRecorder.Builder shadedBuilder;

    protected Builder(final io.opentelemetry.metrics.LongValueRecorder.Builder shadedBuilder) {
      this.shadedBuilder = shadedBuilder;
    }

    @Override
    public LongValueRecorder.Builder setDescription(final String description) {
      shadedBuilder.setDescription(description);
      return this;
    }

    @Override
    public LongValueRecorder.Builder setUnit(final String unit) {
      shadedBuilder.setUnit(unit);
      return this;
    }

    @Override
    public LongValueRecorder.Builder setConstantLabels(final Map<String, String> constantLabels) {
      shadedBuilder.setConstantLabels(constantLabels);
      return this;
    }

    @Override
    public LongValueRecorder build() {
      return new UnshadedLongValueRecorder(shadedBuilder.build());
    }
  }
}
