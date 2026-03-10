/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_40.incubator.metrics;

import io.opentelemetry.api.incubator.metrics.ExtendedDoubleGauge;
import io.opentelemetry.api.metrics.DoubleGauge;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_38.metrics.ApplicationDoubleGauge138;

final class ApplicationDoubleGauge140Incubator extends ApplicationDoubleGauge138
    implements application.io.opentelemetry.api.incubator.metrics.ExtendedDoubleGauge {

  private final DoubleGauge agentDoubleGauge;

  ApplicationDoubleGauge140Incubator(DoubleGauge agentDoubleGauge) {
    super(agentDoubleGauge);
    this.agentDoubleGauge = agentDoubleGauge;
  }

  @Override
  public boolean isEnabled() {
    return ((ExtendedDoubleGauge) agentDoubleGauge).isEnabled();
  }
}
