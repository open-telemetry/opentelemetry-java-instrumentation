/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import io.opentelemetry.api.metrics.ObservableDoubleCounter;

public final class ApplicationObservableDoubleCounter
    implements application.io.opentelemetry.api.metrics.ObservableDoubleCounter {

  private final ObservableDoubleCounter agentCounter;
  private final Runnable onClose;

  public ApplicationObservableDoubleCounter(
      ObservableDoubleCounter agentCounter, Runnable onClose) {
    this.agentCounter = agentCounter;
    this.onClose = onClose;
  }

  // not adding @Override because this method was introduced in 1.12
  @SuppressWarnings("unused")
  public void close() {
    agentCounter.close();
    onClose.run();
  }
}
