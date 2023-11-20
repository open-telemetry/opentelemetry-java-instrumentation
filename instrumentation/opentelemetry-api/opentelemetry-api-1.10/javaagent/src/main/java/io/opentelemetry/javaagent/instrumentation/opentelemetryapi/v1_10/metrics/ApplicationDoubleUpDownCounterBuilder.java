/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import application.io.opentelemetry.api.metrics.DoubleUpDownCounter;
import application.io.opentelemetry.api.metrics.DoubleUpDownCounterBuilder;
import application.io.opentelemetry.api.metrics.ObservableDoubleMeasurement;
import application.io.opentelemetry.api.metrics.ObservableDoubleUpDownCounter;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.function.Consumer;

public class ApplicationDoubleUpDownCounterBuilder implements DoubleUpDownCounterBuilder {

  private final io.opentelemetry.api.metrics.DoubleUpDownCounterBuilder agentBuilder;

  protected ApplicationDoubleUpDownCounterBuilder(
      io.opentelemetry.api.metrics.DoubleUpDownCounterBuilder agentBuilder) {
    this.agentBuilder = agentBuilder;
  }

  @Override
  @CanIgnoreReturnValue
  public DoubleUpDownCounterBuilder setDescription(String description) {
    agentBuilder.setDescription(description);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public DoubleUpDownCounterBuilder setUnit(String unit) {
    agentBuilder.setUnit(unit);
    return this;
  }

  @Override
  public DoubleUpDownCounter build() {
    return new ApplicationDoubleUpDownCounter(agentBuilder.build());
  }

  @Override
  public ObservableDoubleUpDownCounter buildWithCallback(
      Consumer<ObservableDoubleMeasurement> applicationCallback) {
    return new ApplicationObservableDoubleUpDownCounter(
        agentBuilder.buildWithCallback(
            agentMeasurement ->
                applicationCallback.accept(
                    new ApplicationObservableDoubleMeasurement(agentMeasurement))));
  }

  // added in 1.15.0
  public ObservableDoubleMeasurement buildObserver() {
    return new ApplicationObservableDoubleMeasurement(agentBuilder.buildObserver());
  }
}
