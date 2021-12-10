/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import application.io.opentelemetry.api.metrics.DoubleUpDownCounter;
import application.io.opentelemetry.api.metrics.DoubleUpDownCounterBuilder;
import application.io.opentelemetry.api.metrics.ObservableDoubleMeasurement;
import java.util.function.Consumer;

final class ApplicationDoubleUpDownCounterBuilder implements DoubleUpDownCounterBuilder {

  private final io.opentelemetry.api.metrics.DoubleUpDownCounterBuilder agentBuilder;

  ApplicationDoubleUpDownCounterBuilder(
      io.opentelemetry.api.metrics.DoubleUpDownCounterBuilder agentBuilder) {
    this.agentBuilder = agentBuilder;
  }

  @Override
  public DoubleUpDownCounterBuilder setDescription(String description) {
    agentBuilder.setDescription(description);
    return this;
  }

  @Override
  public DoubleUpDownCounterBuilder setUnit(String unit) {
    agentBuilder.setUnit(unit);
    return this;
  }

  @Override
  public DoubleUpDownCounter build() {
    return new ApplicationDoubleUpDownCounter(agentBuilder.build());
  }

  @Override
  public void buildWithCallback(Consumer<ObservableDoubleMeasurement> applicationCallback) {
    agentBuilder.buildWithCallback(
        agentMeasurement ->
            applicationCallback.accept(
                new ApplicationObservableDoubleMeasurement(agentMeasurement)));
  }
}
