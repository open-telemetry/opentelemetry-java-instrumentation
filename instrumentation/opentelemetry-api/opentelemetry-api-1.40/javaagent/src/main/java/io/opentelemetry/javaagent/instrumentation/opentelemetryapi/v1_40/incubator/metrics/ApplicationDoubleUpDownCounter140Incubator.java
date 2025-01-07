/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_40.incubator.metrics;

import application.io.opentelemetry.api.incubator.metrics.ExtendedDoubleUpDownCounter;
import io.opentelemetry.api.metrics.DoubleUpDownCounter;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationDoubleUpDownCounter;

final class ApplicationDoubleUpDownCounter140Incubator extends ApplicationDoubleUpDownCounter
    implements ExtendedDoubleUpDownCounter {

  private final io.opentelemetry.api.metrics.DoubleUpDownCounter agentCounter;

  ApplicationDoubleUpDownCounter140Incubator(DoubleUpDownCounter agentCounter) {
    super(agentCounter);
    this.agentCounter = agentCounter;
  }

  @Override
  public boolean isEnabled() {
    return ((io.opentelemetry.api.incubator.metrics.ExtendedDoubleUpDownCounter) agentCounter)
        .isEnabled();
  }
}
