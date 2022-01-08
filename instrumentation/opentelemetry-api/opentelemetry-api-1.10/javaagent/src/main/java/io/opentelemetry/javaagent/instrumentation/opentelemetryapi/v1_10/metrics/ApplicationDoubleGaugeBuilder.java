/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import application.io.opentelemetry.api.metrics.DoubleGaugeBuilder;
import application.io.opentelemetry.api.metrics.LongGaugeBuilder;
import application.io.opentelemetry.api.metrics.ObservableDoubleGauge;
import application.io.opentelemetry.api.metrics.ObservableDoubleMeasurement;
import java.util.function.Consumer;

final class ApplicationDoubleGaugeBuilder implements DoubleGaugeBuilder {

  private final io.opentelemetry.api.metrics.DoubleGaugeBuilder agentBuilder;

  ApplicationDoubleGaugeBuilder(io.opentelemetry.api.metrics.DoubleGaugeBuilder agentBuilder) {
    this.agentBuilder = agentBuilder;
  }

  @Override
  public DoubleGaugeBuilder setDescription(String description) {
    agentBuilder.setDescription(description);
    return this;
  }

  @Override
  public DoubleGaugeBuilder setUnit(String unit) {
    agentBuilder.setUnit(unit);
    return this;
  }

  @Override
  public LongGaugeBuilder ofLongs() {
    return new ApplicationLongGaugeBuilder(agentBuilder.ofLongs());
  }

  @Override
  public ObservableDoubleGauge buildWithCallback(
      Consumer<ObservableDoubleMeasurement> applicationCallback) {
    return new ApplicationObservableDoubleGauge(
        agentBuilder.buildWithCallback(
            agentMeasurement ->
                applicationCallback.accept(
                    new ApplicationObservableDoubleMeasurement(agentMeasurement))));
  }
}
