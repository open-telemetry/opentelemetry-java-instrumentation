/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import io.opentelemetry.api.metrics.ObservableDoubleUpDownCounter;

public final class ApplicationObservableDoubleUpDownCounter
    implements application.io.opentelemetry.api.metrics.ObservableDoubleUpDownCounter {

  private final ObservableDoubleUpDownCounter agentUpDownCounter;
  private final Runnable onClose;

  public ApplicationObservableDoubleUpDownCounter(
      ObservableDoubleUpDownCounter agentUpDownCounter, Runnable onClose) {
    this.agentUpDownCounter = agentUpDownCounter;
    this.onClose = onClose;
  }

  // not adding @Override because this method was introduced in 1.12
  @SuppressWarnings("unused")
  public void close() {
    agentUpDownCounter.close();
    onClose.run();
  }
}
