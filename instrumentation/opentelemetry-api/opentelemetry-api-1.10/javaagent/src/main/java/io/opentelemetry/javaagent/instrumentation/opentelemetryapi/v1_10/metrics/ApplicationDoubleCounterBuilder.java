/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import application.io.opentelemetry.api.metrics.DoubleCounter;
import application.io.opentelemetry.api.metrics.DoubleCounterBuilder;
import application.io.opentelemetry.api.metrics.ObservableDoubleCounter;
import application.io.opentelemetry.api.metrics.ObservableDoubleMeasurement;
import java.util.function.Consumer;

final class ApplicationDoubleCounterBuilder implements DoubleCounterBuilder {

  private final io.opentelemetry.api.metrics.DoubleCounterBuilder agentBuilder;

  ApplicationDoubleCounterBuilder(io.opentelemetry.api.metrics.DoubleCounterBuilder agentBuilder) {
    this.agentBuilder = agentBuilder;
  }

  @Override
  public DoubleCounterBuilder setDescription(String description) {
    agentBuilder.setDescription(description);
    return this;
  }

  @Override
  public DoubleCounterBuilder setUnit(String unit) {
    agentBuilder.setUnit(unit);
    return this;
  }

  @Override
  public DoubleCounter build() {
    return new ApplicationDoubleCounter(agentBuilder.build());
  }

  @Override
  public ObservableDoubleCounter buildWithCallback(
      Consumer<ObservableDoubleMeasurement> applicationCallback) {
    return new ApplicationObservableDoubleCounter(
        agentBuilder.buildWithCallback(
            agentMeasurement ->
                applicationCallback.accept(
                    new ApplicationObservableDoubleMeasurement(agentMeasurement))));
  }
}
