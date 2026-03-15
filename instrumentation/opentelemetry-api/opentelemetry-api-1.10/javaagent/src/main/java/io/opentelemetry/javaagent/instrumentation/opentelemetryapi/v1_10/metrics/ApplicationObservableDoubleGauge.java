/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import io.opentelemetry.api.metrics.ObservableDoubleGauge;

public final class ApplicationObservableDoubleGauge
    implements application.io.opentelemetry.api.metrics.ObservableDoubleGauge {

  private final ObservableDoubleGauge agentGauge;

  public ApplicationObservableDoubleGauge(ObservableDoubleGauge agentGauge) {
    this.agentGauge = agentGauge;
  }

  // not adding @Override because this method was introduced in 1.12
  @SuppressWarnings("unused")
  public void close() {
    agentGauge.close();
  }
}
