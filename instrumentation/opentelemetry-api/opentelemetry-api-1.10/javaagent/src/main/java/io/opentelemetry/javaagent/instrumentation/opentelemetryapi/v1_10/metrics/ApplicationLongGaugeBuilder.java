/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import application.io.opentelemetry.api.metrics.LongGaugeBuilder;
import application.io.opentelemetry.api.metrics.ObservableLongGauge;
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
  public ObservableLongGauge buildWithCallback(
      Consumer<ObservableLongMeasurement> applicationCallback) {
    return new ApplicationObservableLongGauge(
        agentBuilder.buildWithCallback(
            agentMeasurement ->
                applicationCallback.accept(
                    new ApplicationObservableLongMeasurement(agentMeasurement))));
  }
}
