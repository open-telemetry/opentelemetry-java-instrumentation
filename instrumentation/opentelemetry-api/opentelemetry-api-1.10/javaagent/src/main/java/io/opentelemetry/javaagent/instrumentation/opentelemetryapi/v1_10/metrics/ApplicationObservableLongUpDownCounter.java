/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import io.opentelemetry.api.metrics.ObservableLongUpDownCounter;

public final class ApplicationObservableLongUpDownCounter
    implements application.io.opentelemetry.api.metrics.ObservableLongUpDownCounter {

  private final ObservableLongUpDownCounter agentUpDownCounter;

  public ApplicationObservableLongUpDownCounter(ObservableLongUpDownCounter agentUpDownCounter) {
    this.agentUpDownCounter = agentUpDownCounter;
  }

  // not adding @Override because this method was introduced in 1.12
  @SuppressWarnings("unused")
  public void close() {
    agentUpDownCounter.close();
  }
}
