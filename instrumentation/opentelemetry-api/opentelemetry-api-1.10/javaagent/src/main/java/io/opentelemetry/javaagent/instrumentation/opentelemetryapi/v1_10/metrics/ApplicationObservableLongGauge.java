/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import application.io.opentelemetry.api.metrics.ObservableLongGauge;

public final class ApplicationObservableLongGauge implements ObservableLongGauge {

  private final io.opentelemetry.api.metrics.ObservableLongGauge agentGauge;

  public ApplicationObservableLongGauge(
      io.opentelemetry.api.metrics.ObservableLongGauge agentGauge) {
    this.agentGauge = agentGauge;
  }

  // not adding @Override because this method was introduced in 1.12
  @SuppressWarnings("unused")
  public void close() {
    agentGauge.close();
  }
}
