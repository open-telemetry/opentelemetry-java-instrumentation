/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_40.incubator.metrics;

import io.opentelemetry.api.incubator.metrics.ExtendedLongGauge;
import io.opentelemetry.api.metrics.LongGauge;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_38.metrics.ApplicationLongGauge138;

final class ApplicationLongGauge140Incubator extends ApplicationLongGauge138
    implements application.io.opentelemetry.api.incubator.metrics.ExtendedLongGauge {

  private final LongGauge agentLongGauge;

  ApplicationLongGauge140Incubator(LongGauge agentLongGauge) {
    super(agentLongGauge);
    this.agentLongGauge = agentLongGauge;
  }

  @Override
  public boolean isEnabled() {
    return ((ExtendedLongGauge) agentLongGauge).isEnabled();
  }
}
