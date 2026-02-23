/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.metrics.DoubleCounterBuilder;
import java.util.function.Consumer;

public class ApplicationDoubleCounterBuilder
    implements application.io.opentelemetry.api.metrics.DoubleCounterBuilder {

  private final DoubleCounterBuilder agentBuilder;

  protected ApplicationDoubleCounterBuilder(DoubleCounterBuilder agentBuilder) {
    this.agentBuilder = agentBuilder;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.metrics.DoubleCounterBuilder setDescription(
      String description) {
    agentBuilder.setDescription(description);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.metrics.DoubleCounterBuilder setUnit(String unit) {
    agentBuilder.setUnit(unit);
    return this;
  }

  @Override
  public application.io.opentelemetry.api.metrics.DoubleCounter build() {
    return new ApplicationDoubleCounter(agentBuilder.build());
  }

  @Override
  public application.io.opentelemetry.api.metrics.ObservableDoubleCounter buildWithCallback(
      Consumer<application.io.opentelemetry.api.metrics.ObservableDoubleMeasurement>
          applicationCallback) {
    return new ApplicationObservableDoubleCounter(
        CallbackAnchor.anchor(
            agentBuilder::buildWithCallback,
            agentMeasurement ->
                applicationCallback.accept(
                    new ApplicationObservableDoubleMeasurement(agentMeasurement))));
  }

  // added in 1.15.0
  public application.io.opentelemetry.api.metrics.ObservableDoubleMeasurement buildObserver() {
    return new ApplicationObservableDoubleMeasurement(agentBuilder.buildObserver());
  }
}
