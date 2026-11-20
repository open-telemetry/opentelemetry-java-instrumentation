/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import io.opentelemetry.api.metrics.ObservableDoubleUpDownCounter;

public class ApplicationObservableDoubleUpDownCounter
    implements application.io.opentelemetry.api.metrics.ObservableDoubleUpDownCounter {

  private final ApplicationCallbackHandle callbackHandle;

  public ApplicationObservableDoubleUpDownCounter(
      ObservableDoubleUpDownCounter agentUpDownCounter, Runnable releaseAnchor) {
    callbackHandle = new ApplicationCallbackHandle(agentUpDownCounter::close, releaseAnchor);
  }

  // not adding @Override because this method was introduced in 1.12
  @SuppressWarnings("unused")
  public void close() {
    callbackHandle.close();
  }
}
