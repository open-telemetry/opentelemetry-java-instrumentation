/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.metrics.DoubleGaugeBuilder;
import java.util.function.Consumer;

public class ApplicationDoubleGaugeBuilder
    implements application.io.opentelemetry.api.metrics.DoubleGaugeBuilder {

  private final DoubleGaugeBuilder agentBuilder;

  protected ApplicationDoubleGaugeBuilder(DoubleGaugeBuilder agentBuilder) {
    this.agentBuilder = agentBuilder;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.metrics.DoubleGaugeBuilder setDescription(
      String description) {
    agentBuilder.setDescription(description);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.metrics.DoubleGaugeBuilder setUnit(String unit) {
    agentBuilder.setUnit(unit);
    return this;
  }

  @Override
  public application.io.opentelemetry.api.metrics.LongGaugeBuilder ofLongs() {
    return new ApplicationLongGaugeBuilder(agentBuilder.ofLongs());
  }

  @Override
  public application.io.opentelemetry.api.metrics.ObservableDoubleGauge buildWithCallback(
      Consumer<application.io.opentelemetry.api.metrics.ObservableDoubleMeasurement>
          applicationCallback) {
    return new ApplicationObservableDoubleGauge(
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
