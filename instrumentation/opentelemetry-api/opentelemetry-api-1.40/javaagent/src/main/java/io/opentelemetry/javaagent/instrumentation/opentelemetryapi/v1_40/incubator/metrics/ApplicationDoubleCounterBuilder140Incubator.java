/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_40.incubator.metrics;

import application.io.opentelemetry.api.metrics.DoubleCounter;
import io.opentelemetry.api.metrics.DoubleCounterBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_37.incubator.metrics.ApplicationDoubleCounterBuilder137;

final class ApplicationDoubleCounterBuilder140Incubator extends ApplicationDoubleCounterBuilder137 {
  private final io.opentelemetry.api.metrics.DoubleCounterBuilder agentBuilder;

  ApplicationDoubleCounterBuilder140Incubator(DoubleCounterBuilder agentBuilder) {
    super(agentBuilder);
    this.agentBuilder = agentBuilder;
  }

  @Override
  public DoubleCounter build() {
    return new ApplicationDoubleCounter140Incubator(agentBuilder.build());
  }
}
