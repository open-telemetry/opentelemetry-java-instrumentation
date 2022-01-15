/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import application.io.opentelemetry.api.metrics.DoubleUpDownCounterBuilder;
import application.io.opentelemetry.api.metrics.LongUpDownCounter;
import application.io.opentelemetry.api.metrics.LongUpDownCounterBuilder;
import application.io.opentelemetry.api.metrics.ObservableLongMeasurement;
import application.io.opentelemetry.api.metrics.ObservableLongUpDownCounter;
import java.util.function.Consumer;

final class ApplicationLongUpDownCounterBuilder implements LongUpDownCounterBuilder {

  private final io.opentelemetry.api.metrics.LongUpDownCounterBuilder agentBuilder;

  ApplicationLongUpDownCounterBuilder(
      io.opentelemetry.api.metrics.LongUpDownCounterBuilder agentBuilder) {
    this.agentBuilder = agentBuilder;
  }

  @Override
  public LongUpDownCounterBuilder setDescription(String description) {
    agentBuilder.setDescription(description);
    return this;
  }

  @Override
  public LongUpDownCounterBuilder setUnit(String unit) {
    agentBuilder.setUnit(unit);
    return this;
  }

  @Override
  public DoubleUpDownCounterBuilder ofDoubles() {
    return new ApplicationDoubleUpDownCounterBuilder(agentBuilder.ofDoubles());
  }

  @Override
  public LongUpDownCounter build() {
    return new ApplicationLongUpDownCounter(agentBuilder.build());
  }

  @Override
  public ObservableLongUpDownCounter buildWithCallback(
      Consumer<ObservableLongMeasurement> applicationCallback) {
    return new ApplicationObservableLongUpDownCounter(
        agentBuilder.buildWithCallback(
            agentMeasurement ->
                applicationCallback.accept(
                    new ApplicationObservableLongMeasurement(agentMeasurement))));
  }
}
