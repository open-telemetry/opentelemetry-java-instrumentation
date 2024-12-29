/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_40.incubator.metrics;

import application.io.opentelemetry.api.metrics.DoubleUpDownCounterBuilder;
import application.io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.LongUpDownCounterBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_37.incubator.metrics.ApplicationLongUpDownCounterBuilder137;

final class ApplicationLongUpDownCounterBuilder140Incubator
    extends ApplicationLongUpDownCounterBuilder137 {

  private final io.opentelemetry.api.metrics.LongUpDownCounterBuilder agentBuilder;

  ApplicationLongUpDownCounterBuilder140Incubator(LongUpDownCounterBuilder agentBuilder) {
    super(agentBuilder);
    this.agentBuilder = agentBuilder;
  }

  @Override
  public DoubleUpDownCounterBuilder ofDoubles() {
    return new ApplicationDoubleUpDownCounterBuilder140Incubator(agentBuilder.ofDoubles());
  }

  @Override
  public LongUpDownCounter build() {
    return new ApplicationLongUpDownCounter140Incubator(agentBuilder.build());
  }
}
