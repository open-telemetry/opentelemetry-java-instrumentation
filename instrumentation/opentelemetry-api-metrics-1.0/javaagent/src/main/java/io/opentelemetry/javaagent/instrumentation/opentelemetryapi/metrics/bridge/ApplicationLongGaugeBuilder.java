/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.metrics.bridge;

import application.io.opentelemetry.api.metrics.DoubleGaugeBuilder;
import application.io.opentelemetry.api.metrics.LongGaugeBuilder;
import application.io.opentelemetry.api.metrics.ObservableLongMeasurement;
import java.util.function.Consumer;

final class ApplicationLongGaugeBuilder implements LongGaugeBuilder {

  private final io.opentelemetry.api.metrics.LongGaugeBuilder agentBuilder;

  ApplicationLongGaugeBuilder(io.opentelemetry.api.metrics.LongGaugeBuilder agentBuilder) {
    this.agentBuilder = agentBuilder;
  }

  @Override
  public LongGaugeBuilder setDescription(String description) {
    agentBuilder.setDescription(description);
    return this;
  }

  @Override
  public LongGaugeBuilder setUnit(String unit) {
    agentBuilder.setUnit(unit);
    return this;
  }

  @Override
  public DoubleGaugeBuilder ofDoubles() {
    return new ApplicationDoubleGaugeBuilder(agentBuilder.ofDoubles());
  }

  @Override
  public void buildWithCallback(Consumer<ObservableLongMeasurement> applicationCallback) {
    agentBuilder.buildWithCallback(
        agentMeasurement ->
            applicationCallback.accept(new ApplicationObservableLongMeasurement(agentMeasurement)));
  }
}
