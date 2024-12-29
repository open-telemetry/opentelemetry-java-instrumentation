/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_40.incubator.metrics;

import application.io.opentelemetry.api.metrics.DoubleUpDownCounter;
import io.opentelemetry.api.metrics.DoubleUpDownCounterBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_37.incubator.metrics.ApplicationDoubleUpDownCounterBuilder137;

final class ApplicationDoubleUpDownCounterBuilder140Incubator
    extends ApplicationDoubleUpDownCounterBuilder137 {

  private final io.opentelemetry.api.metrics.DoubleUpDownCounterBuilder agentBuilder;

  ApplicationDoubleUpDownCounterBuilder140Incubator(DoubleUpDownCounterBuilder agentBuilder) {
    super(agentBuilder);
    this.agentBuilder = agentBuilder;
  }

  @Override
  public DoubleUpDownCounter build() {
    return new ApplicationDoubleUpDownCounter140Incubator(agentBuilder.build());
  }
}
