/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import application.io.opentelemetry.api.metrics.ObservableDoubleUpDownCounter;

public final class ApplicationObservableDoubleUpDownCounter
    implements ObservableDoubleUpDownCounter {

  private final io.opentelemetry.api.metrics.ObservableDoubleUpDownCounter agentUpDownCounter;

  public ApplicationObservableDoubleUpDownCounter(
      io.opentelemetry.api.metrics.ObservableDoubleUpDownCounter agentUpDownCounter) {
    this.agentUpDownCounter = agentUpDownCounter;
  }

  // not adding @Override because this method was introduced in 1.12
  @SuppressWarnings("unused")
  public void close() {
    agentUpDownCounter.close();
  }
}
