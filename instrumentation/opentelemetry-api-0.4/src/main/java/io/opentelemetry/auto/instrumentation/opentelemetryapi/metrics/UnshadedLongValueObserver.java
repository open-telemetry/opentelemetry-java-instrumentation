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
import unshaded.io.opentelemetry.metrics.LongValueObserver;

class UnshadedLongValueObserver implements LongValueObserver {

  private final io.opentelemetry.metrics.LongValueObserver shadedLongValueObserver;

  UnshadedLongValueObserver(
      final io.opentelemetry.metrics.LongValueObserver shadedLongValueObserver) {
    this.shadedLongValueObserver = shadedLongValueObserver;
  }

  @Override
  public void setCallback(final Callback<ResultLongValueObserver> metricUpdater) {
    shadedLongValueObserver.setCallback(new ShadedResultLongValueObserver(metricUpdater));
  }

  private static class ShadedResultLongValueObserver
      implements io.opentelemetry.metrics.AsynchronousInstrument.Callback<
      io.opentelemetry.metrics.LongValueObserver.ResultLongValueObserver> {

    private final Callback<ResultLongValueObserver> metricUpdater;

    ShadedResultLongValueObserver(final Callback<ResultLongValueObserver> metricUpdater) {
      this.metricUpdater = metricUpdater;
    }

    @Override
    public void update(final io.opentelemetry.metrics.LongValueObserver.ResultLongValueObserver result) {
      metricUpdater.update(new UnshadedResultLongValueObserver(result));
    }
  }

  private static class UnshadedResultLongValueObserver implements ResultLongValueObserver {

    private final io.opentelemetry.metrics.LongValueObserver.ResultLongValueObserver shadedResultLongValueObserver;

    UnshadedResultLongValueObserver(
        final io.opentelemetry.metrics.LongValueObserver.ResultLongValueObserver shadedResultLongValueObserver) {
      this.shadedResultLongValueObserver = shadedResultLongValueObserver;
    }

    @Override
    public void observe(final long value, final String... keyValueLabelPairs) {
      shadedResultLongValueObserver.observe(value, keyValueLabelPairs);
    }
  }

  static class Builder implements LongValueObserver.Builder {

    private final io.opentelemetry.metrics.LongValueObserver.Builder shadedBuilder;

    Builder(final io.opentelemetry.metrics.LongValueObserver.Builder shadedBuilder) {
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
    public LongValueObserver.Builder setConstantLabels(final Map<String, String> constantLabels) {
      shadedBuilder.setConstantLabels(constantLabels);
      return this;
    }

    @Override
    public LongValueObserver build() {
      return new UnshadedLongValueObserver(shadedBuilder.build());
    }
  }
}
