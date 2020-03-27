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
import unshaded.io.opentelemetry.metrics.LongObserver;

class UnshadedLongObserver implements LongObserver {

  private final io.opentelemetry.metrics.LongObserver shadedLongObserver;

  UnshadedLongObserver(final io.opentelemetry.metrics.LongObserver shadedLongObserver) {
    this.shadedLongObserver = shadedLongObserver;
  }

  @Override
  public void setCallback(final Callback<ResultLongObserver> metricUpdater) {
    shadedLongObserver.setCallback(new ShadedResultLongObserver(metricUpdater));
  }

  private static class ShadedResultLongObserver
      implements io.opentelemetry.metrics.Observer.Callback<
          io.opentelemetry.metrics.LongObserver.ResultLongObserver> {

    private final Callback<ResultLongObserver> metricUpdater;

    ShadedResultLongObserver(final Callback<ResultLongObserver> metricUpdater) {
      this.metricUpdater = metricUpdater;
    }

    @Override
    public void update(final io.opentelemetry.metrics.LongObserver.ResultLongObserver result) {
      System.out.println(result.getClass());
      metricUpdater.update(new UnshadedResultLongObserver(result));
    }
  }

  private static class UnshadedResultLongObserver implements ResultLongObserver {

    private final io.opentelemetry.metrics.LongObserver.ResultLongObserver shadedResultLongObserver;

    UnshadedResultLongObserver(
        final io.opentelemetry.metrics.LongObserver.ResultLongObserver shadedResultLongObserver) {
      this.shadedResultLongObserver = shadedResultLongObserver;
    }

    @Override
    public void observe(final long value, final String... keyValueLabelPairs) {
      shadedResultLongObserver.observe(value, keyValueLabelPairs);
    }
  }

  static class Builder implements LongObserver.Builder {

    private final io.opentelemetry.metrics.LongObserver.Builder shadedBuilder;

    Builder(final io.opentelemetry.metrics.LongObserver.Builder shadedBuilder) {
      this.shadedBuilder = shadedBuilder;
    }

    @Override
    public LongObserver.Builder setDescription(final String description) {
      shadedBuilder.setDescription(description);
      return this;
    }

    @Override
    public LongObserver.Builder setUnit(final String unit) {
      shadedBuilder.setUnit(unit);
      return this;
    }

    @Override
    public LongObserver.Builder setLabelKeys(final List<String> labelKeys) {
      shadedBuilder.setLabelKeys(labelKeys);
      return this;
    }

    @Override
    public LongObserver.Builder setConstantLabels(final Map<String, String> constantLabels) {
      shadedBuilder.setConstantLabels(constantLabels);
      return this;
    }

    @Override
    public LongObserver.Builder setMonotonic(final boolean monotonic) {
      shadedBuilder.setMonotonic(monotonic);
      return this;
    }

    @Override
    public LongObserver build() {
      return new UnshadedLongObserver(shadedBuilder.build());
    }
  }
}
