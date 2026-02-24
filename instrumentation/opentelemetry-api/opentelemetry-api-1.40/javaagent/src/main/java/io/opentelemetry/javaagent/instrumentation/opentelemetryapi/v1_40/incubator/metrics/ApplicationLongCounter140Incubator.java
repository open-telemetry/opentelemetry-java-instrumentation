/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_40.incubator.metrics;

import application.io.opentelemetry.api.incubator.metrics.ExtendedLongCounter;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationLongCounter;

final class ApplicationLongCounter140Incubator extends ApplicationLongCounter
    implements ExtendedLongCounter {
  private final io.opentelemetry.api.metrics.LongCounter agentCounter;

  ApplicationLongCounter140Incubator(LongCounter agentCounter) {
    super(agentCounter);
    this.agentCounter = agentCounter;
  }

  @Override
  public boolean isEnabled() {
    return ((io.opentelemetry.api.incubator.metrics.ExtendedLongCounter) agentCounter).isEnabled();
  }
}
