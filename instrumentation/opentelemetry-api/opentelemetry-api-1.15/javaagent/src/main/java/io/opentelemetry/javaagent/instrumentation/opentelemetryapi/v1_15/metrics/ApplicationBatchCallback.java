/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_15.metrics;

import io.opentelemetry.api.metrics.BatchCallback;

final class ApplicationBatchCallback
    implements application.io.opentelemetry.api.metrics.BatchCallback {
  private final BatchCallback agentCallback;

  ApplicationBatchCallback(BatchCallback agentCallback) {
    this.agentCallback = agentCallback;
  }

  @Override
  public void close() {
    agentCallback.close();
  }
}
