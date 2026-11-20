/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_15.metrics;

import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationCallbackHandle;

final class ApplicationBatchCallback
    implements application.io.opentelemetry.api.metrics.BatchCallback {
  private final ApplicationCallbackHandle callbackHandle;

  ApplicationBatchCallback(BatchCallback agentCallback, Runnable onClose) {
    callbackHandle = new ApplicationCallbackHandle(agentCallback::close, onClose);
  }

  @Override
  public void close() {
    callbackHandle.close();
  }
}
