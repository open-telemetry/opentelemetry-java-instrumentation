/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_40.incubator.metrics;

import application.io.opentelemetry.api.incubator.metrics.ExtendedDoubleHistogram;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationDoubleHistogram;

final class ApplicationDoubleHistogram140Incubator extends ApplicationDoubleHistogram
    implements ExtendedDoubleHistogram {

  private final io.opentelemetry.api.metrics.DoubleHistogram agentHistogram;

  ApplicationDoubleHistogram140Incubator(DoubleHistogram agentHistogram) {
    super(agentHistogram);
    this.agentHistogram = agentHistogram;
  }

  @Override
  public boolean isEnabled() {
    return ((io.opentelemetry.api.incubator.metrics.ExtendedDoubleHistogram) agentHistogram)
        .isEnabled();
  }
}
