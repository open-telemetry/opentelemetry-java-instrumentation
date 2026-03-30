/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import io.opentelemetry.api.metrics.ObservableLongCounter;

public final class ApplicationObservableLongCounter
    implements application.io.opentelemetry.api.metrics.ObservableLongCounter {

  private final ObservableLongCounter agentCounter;
  private final Runnable onClose;

  public ApplicationObservableLongCounter(ObservableLongCounter agentCounter, Runnable onClose) {
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
