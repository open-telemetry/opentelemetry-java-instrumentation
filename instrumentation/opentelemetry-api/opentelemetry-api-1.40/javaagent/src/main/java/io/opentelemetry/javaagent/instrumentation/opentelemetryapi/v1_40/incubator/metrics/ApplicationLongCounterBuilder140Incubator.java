/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_40.incubator.metrics;

import application.io.opentelemetry.api.metrics.DoubleCounterBuilder;
import application.io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongCounterBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_37.incubator.metrics.ApplicationLongCounterBuilder137;

final class ApplicationLongCounterBuilder140Incubator extends ApplicationLongCounterBuilder137 {
  private final io.opentelemetry.api.metrics.LongCounterBuilder agentBuilder;

  ApplicationLongCounterBuilder140Incubator(LongCounterBuilder agentBuilder) {
    super(agentBuilder);
    this.agentBuilder = agentBuilder;
  }

  @Override
  public DoubleCounterBuilder ofDoubles() {
    return new ApplicationDoubleCounterBuilder140Incubator(agentBuilder.ofDoubles());
  }

  @Override
  public LongCounter build() {
    return new ApplicationLongCounter140Incubator(agentBuilder.build());
  }
}
