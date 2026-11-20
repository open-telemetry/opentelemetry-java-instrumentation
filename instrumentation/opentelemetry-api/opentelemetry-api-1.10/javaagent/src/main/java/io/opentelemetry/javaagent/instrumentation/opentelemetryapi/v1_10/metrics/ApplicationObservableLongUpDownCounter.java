/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import io.opentelemetry.api.metrics.ObservableLongUpDownCounter;

public class ApplicationObservableLongUpDownCounter
    implements application.io.opentelemetry.api.metrics.ObservableLongUpDownCounter {

  private final ApplicationCallbackHandle callbackHandle;

  public ApplicationObservableLongUpDownCounter(
      ObservableLongUpDownCounter agentUpDownCounter, Runnable onClose) {
    callbackHandle = new ApplicationCallbackHandle(agentUpDownCounter::close, onClose);
  }

  // not adding @Override because this method was introduced in 1.12
  @SuppressWarnings("unused")
  public void close() {
    callbackHandle.close();
  }
}
