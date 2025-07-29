/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_40.incubator.metrics;

import application.io.opentelemetry.api.incubator.metrics.ExtendedLongGauge;
import io.opentelemetry.api.metrics.LongGauge;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_38.metrics.ApplicationLongGauge138;

final class ApplicationLongGauge140Incubator extends ApplicationLongGauge138
    implements ExtendedLongGauge {

  private final io.opentelemetry.api.metrics.LongGauge agentLongGauge;

  ApplicationLongGauge140Incubator(LongGauge agentLongGauge) {
    super(agentLongGauge);
    this.agentLongGauge = agentLongGauge;
  }

  @Override
  public boolean isEnabled() {
    return ((io.opentelemetry.api.incubator.metrics.ExtendedLongGauge) agentLongGauge).isEnabled();
  }
}
