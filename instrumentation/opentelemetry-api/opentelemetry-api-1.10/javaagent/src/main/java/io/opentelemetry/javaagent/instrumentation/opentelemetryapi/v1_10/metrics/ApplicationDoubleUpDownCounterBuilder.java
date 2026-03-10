/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.metrics.DoubleUpDownCounterBuilder;
import java.util.function.Consumer;

public class ApplicationDoubleUpDownCounterBuilder
    implements application.io.opentelemetry.api.metrics.DoubleUpDownCounterBuilder {

  private final DoubleUpDownCounterBuilder agentBuilder;

  protected ApplicationDoubleUpDownCounterBuilder(DoubleUpDownCounterBuilder agentBuilder) {
    this.agentBuilder = agentBuilder;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.metrics.DoubleUpDownCounterBuilder setDescription(
      String description) {
    agentBuilder.setDescription(description);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.metrics.DoubleUpDownCounterBuilder setUnit(String unit) {
    agentBuilder.setUnit(unit);
    return this;
  }

  @Override
  public application.io.opentelemetry.api.metrics.DoubleUpDownCounter build() {
    return new ApplicationDoubleUpDownCounter(agentBuilder.build());
  }

  @Override
  public application.io.opentelemetry.api.metrics.ObservableDoubleUpDownCounter buildWithCallback(
      Consumer<application.io.opentelemetry.api.metrics.ObservableDoubleMeasurement>
          applicationCallback) {
    return new ApplicationObservableDoubleUpDownCounter(
        agentBuilder.buildWithCallback(
            agentMeasurement ->
                applicationCallback.accept(
                    new ApplicationObservableDoubleMeasurement(agentMeasurement))));
  }

  // added in 1.15.0
  public application.io.opentelemetry.api.metrics.ObservableDoubleMeasurement buildObserver() {
    return new ApplicationObservableDoubleMeasurement(agentBuilder.buildObserver());
  }
}
