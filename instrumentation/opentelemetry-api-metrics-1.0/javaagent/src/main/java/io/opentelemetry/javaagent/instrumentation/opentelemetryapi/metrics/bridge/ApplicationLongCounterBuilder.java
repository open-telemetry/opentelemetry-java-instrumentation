/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.metrics.bridge;

import application.io.opentelemetry.api.metrics.DoubleCounterBuilder;
import application.io.opentelemetry.api.metrics.LongCounter;
import application.io.opentelemetry.api.metrics.LongCounterBuilder;
import application.io.opentelemetry.api.metrics.ObservableLongMeasurement;
import java.util.function.Consumer;

final class ApplicationLongCounterBuilder implements LongCounterBuilder {

  private final io.opentelemetry.api.metrics.LongCounterBuilder agentBuilder;

  ApplicationLongCounterBuilder(io.opentelemetry.api.metrics.LongCounterBuilder agentBuilder) {
    this.agentBuilder = agentBuilder;
  }

  @Override
  public LongCounterBuilder setDescription(String description) {
    agentBuilder.setDescription(description);
    return this;
  }

  @Override
  public LongCounterBuilder setUnit(String unit) {
    agentBuilder.setUnit(unit);
    return this;
  }

  @Override
  public DoubleCounterBuilder ofDoubles() {
    return new ApplicationDoubleCounterBuilder(agentBuilder.ofDoubles());
  }

  @Override
  public LongCounter build() {
    return new ApplicationLongCounter(agentBuilder.build());
  }

  @Override
  public void buildWithCallback(Consumer<ObservableLongMeasurement> applicationCallback) {
    agentBuilder.buildWithCallback(
        agentMeasurement ->
            applicationCallback.accept(new ApplicationObservableLongMeasurement(agentMeasurement)));
  }
}
