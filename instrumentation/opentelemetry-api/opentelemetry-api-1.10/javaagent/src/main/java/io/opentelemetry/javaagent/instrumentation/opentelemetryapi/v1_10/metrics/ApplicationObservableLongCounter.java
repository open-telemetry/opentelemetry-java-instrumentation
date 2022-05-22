/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import application.io.opentelemetry.api.metrics.ObservableLongCounter;

public final class ApplicationObservableLongCounter implements ObservableLongCounter {

  private final io.opentelemetry.api.metrics.ObservableLongCounter agentCounter;

  public ApplicationObservableLongCounter(
      io.opentelemetry.api.metrics.ObservableLongCounter agentCounter) {
    this.agentCounter = agentCounter;
  }

  // not adding @Override because this method was introduced in 1.12
  @SuppressWarnings("unused")
  public void close() {
    agentCounter.close();
  }
}
