/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import application.io.opentelemetry.api.metrics.ObservableDoubleCounter;

public final class ApplicationObservableDoubleCounter implements ObservableDoubleCounter {

  private final io.opentelemetry.api.metrics.ObservableDoubleCounter agentCounter;

  public ApplicationObservableDoubleCounter(
      io.opentelemetry.api.metrics.ObservableDoubleCounter agentCounter) {
    this.agentCounter = agentCounter;
  }

  // not adding @Override because this method was introduced in 1.12
  @SuppressWarnings("unused")
  public void close() {
    agentCounter.close();
  }
}
