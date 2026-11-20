/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import io.opentelemetry.api.metrics.ObservableLongCounter;

public class ApplicationObservableLongCounter
    implements application.io.opentelemetry.api.metrics.ObservableLongCounter {

  private final ApplicationCallbackHandle callbackHandle;

  public ApplicationObservableLongCounter(
      ObservableLongCounter agentCounter, Runnable releaseAnchor) {
    callbackHandle = new ApplicationCallbackHandle(agentCounter::close, releaseAnchor);
  }

  // not adding @Override because this method was introduced in 1.12
  @SuppressWarnings("unused")
  public void close() {
    callbackHandle.close();
  }
}
