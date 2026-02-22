/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_40.incubator.metrics;

import io.opentelemetry.api.incubator.metrics.ExtendedLongUpDownCounter;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationLongUpDownCounter;

final class ApplicationLongUpDownCounter140Incubator extends ApplicationLongUpDownCounter
    implements application.io.opentelemetry.api.incubator.metrics.ExtendedLongUpDownCounter {

  private final LongUpDownCounter agentCounter;

  ApplicationLongUpDownCounter140Incubator(LongUpDownCounter agentCounter) {
    super(agentCounter);
    this.agentCounter = agentCounter;
  }

  @Override
  public boolean isEnabled() {
    return ((ExtendedLongUpDownCounter) agentCounter).isEnabled();
  }
}
