/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import application.io.opentelemetry.api.metrics.ObservableDoubleGauge;

public final class ApplicationObservableDoubleGauge implements ObservableDoubleGauge {

  private final io.opentelemetry.api.metrics.ObservableDoubleGauge agentGauge;

  public ApplicationObservableDoubleGauge(
      io.opentelemetry.api.metrics.ObservableDoubleGauge agentGauge) {
    this.agentGauge = agentGauge;
  }

  // not adding @Override because this method was introduced in 1.12
  @SuppressWarnings("unused")
  public void close() {
    agentGauge.close();
  }
}
