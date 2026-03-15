/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.metrics.LongUpDownCounterBuilder;
import java.util.function.Consumer;

public class ApplicationLongUpDownCounterBuilder
    implements application.io.opentelemetry.api.metrics.LongUpDownCounterBuilder {

  private final LongUpDownCounterBuilder agentBuilder;

  protected ApplicationLongUpDownCounterBuilder(LongUpDownCounterBuilder agentBuilder) {
    this.agentBuilder = agentBuilder;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.metrics.LongUpDownCounterBuilder setDescription(
      String description) {
    agentBuilder.setDescription(description);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.metrics.LongUpDownCounterBuilder setUnit(String unit) {
    agentBuilder.setUnit(unit);
    return this;
  }

  @Override
  public application.io.opentelemetry.api.metrics.DoubleUpDownCounterBuilder ofDoubles() {
    return new ApplicationDoubleUpDownCounterBuilder(agentBuilder.ofDoubles());
  }

  @Override
  public application.io.opentelemetry.api.metrics.LongUpDownCounter build() {
    return new ApplicationLongUpDownCounter(agentBuilder.build());
  }

  @Override
  public application.io.opentelemetry.api.metrics.ObservableLongUpDownCounter buildWithCallback(
      Consumer<application.io.opentelemetry.api.metrics.ObservableLongMeasurement>
          applicationCallback) {
    return new ApplicationObservableLongUpDownCounter(
        agentBuilder.buildWithCallback(
            agentMeasurement ->
                applicationCallback.accept(
                    new ApplicationObservableLongMeasurement(agentMeasurement))));
  }

  // added in 1.15.0
  public application.io.opentelemetry.api.metrics.ObservableLongMeasurement buildObserver() {
    return new ApplicationObservableLongMeasurement(agentBuilder.buildObserver());
  }
}
