/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.metrics.LongCounterBuilder;
import java.util.function.Consumer;

public class ApplicationLongCounterBuilder
    implements application.io.opentelemetry.api.metrics.LongCounterBuilder {

  private final LongCounterBuilder agentBuilder;

  protected ApplicationLongCounterBuilder(LongCounterBuilder agentBuilder) {
    this.agentBuilder = agentBuilder;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.metrics.LongCounterBuilder setDescription(
      String description) {
    agentBuilder.setDescription(description);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.metrics.LongCounterBuilder setUnit(String unit) {
    agentBuilder.setUnit(unit);
    return this;
  }

  @Override
  public application.io.opentelemetry.api.metrics.DoubleCounterBuilder ofDoubles() {
    return new ApplicationDoubleCounterBuilder(agentBuilder.ofDoubles());
  }

  @Override
  public application.io.opentelemetry.api.metrics.LongCounter build() {
    return new ApplicationLongCounter(agentBuilder.build());
  }

  @Override
  public application.io.opentelemetry.api.metrics.ObservableLongCounter buildWithCallback(
      Consumer<application.io.opentelemetry.api.metrics.ObservableLongMeasurement>
          applicationCallback) {
    return new ApplicationObservableLongCounter(
        CallbackAnchor.anchor(
            agentBuilder::buildWithCallback,
            agentMeasurement ->
                applicationCallback.accept(
                    new ApplicationObservableLongMeasurement(agentMeasurement))));
  }

  // added in 1.15.0
  public application.io.opentelemetry.api.metrics.ObservableLongMeasurement buildObserver() {
    return new ApplicationObservableLongMeasurement(agentBuilder.buildObserver());
  }
}
