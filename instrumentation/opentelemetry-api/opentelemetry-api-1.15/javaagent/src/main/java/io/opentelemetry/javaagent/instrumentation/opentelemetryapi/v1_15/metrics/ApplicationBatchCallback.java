/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_15.metrics;

import io.opentelemetry.api.metrics.BatchCallback;

final class ApplicationBatchCallback
    implements application.io.opentelemetry.api.metrics.BatchCallback {
  private final BatchCallback agentCallback;
  private final Runnable onClose;

  ApplicationBatchCallback(BatchCallback agentCallback, Runnable onClose) {
    this.agentCallback = agentCallback;
    this.onClose = onClose;
  }

  @Override
  public void close() {
    agentCallback.close();
    onClose.run();
  }
}
