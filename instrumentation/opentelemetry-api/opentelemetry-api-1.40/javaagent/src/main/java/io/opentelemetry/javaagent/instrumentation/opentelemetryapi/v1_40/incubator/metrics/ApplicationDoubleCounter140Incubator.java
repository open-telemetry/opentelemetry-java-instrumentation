/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_40.incubator.metrics;

import application.io.opentelemetry.api.incubator.metrics.ExtendedDoubleCounter;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationDoubleCounter;

final class ApplicationDoubleCounter140Incubator extends ApplicationDoubleCounter
    implements ExtendedDoubleCounter {
  private final io.opentelemetry.api.metrics.DoubleCounter agentCounter;

  ApplicationDoubleCounter140Incubator(io.opentelemetry.api.metrics.DoubleCounter agentCounter) {
    super(agentCounter);
    this.agentCounter = agentCounter;
  }

  @Override
  public boolean isEnabled() {
    return ((io.opentelemetry.api.incubator.metrics.ExtendedDoubleCounter) agentCounter)
        .isEnabled();
  }
}
