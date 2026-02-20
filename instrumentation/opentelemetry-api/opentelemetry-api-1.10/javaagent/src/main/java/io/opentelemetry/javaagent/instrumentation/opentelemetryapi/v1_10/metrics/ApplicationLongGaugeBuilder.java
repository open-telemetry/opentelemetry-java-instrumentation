/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.metrics.LongGaugeBuilder;
import java.util.function.Consumer;

public class ApplicationLongGaugeBuilder
    implements application.io.opentelemetry.api.metrics.LongGaugeBuilder {

  private final LongGaugeBuilder agentBuilder;

  protected ApplicationLongGaugeBuilder(LongGaugeBuilder agentBuilder) {
    this.agentBuilder = agentBuilder;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.metrics.LongGaugeBuilder setDescription(
      String description) {
    agentBuilder.setDescription(description);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.metrics.LongGaugeBuilder setUnit(String unit) {
    agentBuilder.setUnit(unit);
    return this;
  }

  @Override
  public application.io.opentelemetry.api.metrics.ObservableLongGauge buildWithCallback(
      Consumer<application.io.opentelemetry.api.metrics.ObservableLongMeasurement>
          applicationCallback) {
    return new ApplicationObservableLongGauge(
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
