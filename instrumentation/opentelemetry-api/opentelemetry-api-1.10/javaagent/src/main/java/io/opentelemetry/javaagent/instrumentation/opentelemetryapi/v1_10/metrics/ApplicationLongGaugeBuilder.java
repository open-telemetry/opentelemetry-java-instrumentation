/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import application.io.opentelemetry.api.metrics.LongGaugeBuilder;
import application.io.opentelemetry.api.metrics.ObservableLongGauge;
import application.io.opentelemetry.api.metrics.ObservableLongMeasurement;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.function.Consumer;

public class ApplicationLongGaugeBuilder implements LongGaugeBuilder {

  private final io.opentelemetry.api.metrics.LongGaugeBuilder agentBuilder;

  protected ApplicationLongGaugeBuilder(
      io.opentelemetry.api.metrics.LongGaugeBuilder agentBuilder) {
    this.agentBuilder = agentBuilder;
  }

  @Override
  @CanIgnoreReturnValue
  public LongGaugeBuilder setDescription(String description) {
    agentBuilder.setDescription(description);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public LongGaugeBuilder setUnit(String unit) {
    agentBuilder.setUnit(unit);
    return this;
  }

  @Override
  public ObservableLongGauge buildWithCallback(
      Consumer<ObservableLongMeasurement> applicationCallback) {
    return new ApplicationObservableLongGauge(
        agentBuilder.buildWithCallback(
            agentMeasurement ->
                applicationCallback.accept(
                    new ApplicationObservableLongMeasurement(agentMeasurement))));
  }

  // added in 1.15.0
  public ObservableLongMeasurement buildObserver() {
    return new ApplicationObservableLongMeasurement(agentBuilder.buildObserver());
  }
}
