/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import io.opentelemetry.api.metrics.ObservableDoubleCounter;

public class ApplicationObservableDoubleCounter
    implements application.io.opentelemetry.api.metrics.ObservableDoubleCounter {

  private final ApplicationCallbackHandle callbackHandle;

  public ApplicationObservableDoubleCounter(
      ObservableDoubleCounter agentCounter, Runnable onClose) {
    callbackHandle = new ApplicationCallbackHandle(agentCounter::close, onClose);
  }

  // not adding @Override because this method was introduced in 1.12
  @SuppressWarnings("unused")
  public void close() {
    callbackHandle.close();
  }
}
