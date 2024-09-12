/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import application.io.opentelemetry.api.metrics.DoubleGaugeBuilder;
import application.io.opentelemetry.api.metrics.LongGaugeBuilder;
import application.io.opentelemetry.api.metrics.ObservableDoubleGauge;
import application.io.opentelemetry.api.metrics.ObservableDoubleMeasurement;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.function.Consumer;

public class ApplicationDoubleGaugeBuilder implements DoubleGaugeBuilder {

  private final io.opentelemetry.api.metrics.DoubleGaugeBuilder agentBuilder;

  protected ApplicationDoubleGaugeBuilder(
      io.opentelemetry.api.metrics.DoubleGaugeBuilder agentBuilder) {
    this.agentBuilder = agentBuilder;
  }

  @Override
  @CanIgnoreReturnValue
  public DoubleGaugeBuilder setDescription(String description) {
    agentBuilder.setDescription(description);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
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

  // added in 1.15.0
  public ObservableDoubleMeasurement buildObserver() {
    return new ApplicationObservableDoubleMeasurement(agentBuilder.buildObserver());
  }
}
