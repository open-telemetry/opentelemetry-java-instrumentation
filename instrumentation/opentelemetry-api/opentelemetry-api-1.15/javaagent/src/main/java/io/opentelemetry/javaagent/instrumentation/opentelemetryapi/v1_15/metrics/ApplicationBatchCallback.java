/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_15.metrics;

import application.io.opentelemetry.api.metrics.BatchCallback;

final class ApplicationBatchCallback implements BatchCallback {
  private final io.opentelemetry.api.metrics.BatchCallback agentCallback;

  ApplicationBatchCallback(io.opentelemetry.api.metrics.BatchCallback agentCallback) {
    this.agentCallback = agentCallback;
  }

  @Override
  public void close() {
    agentCallback.close();
  }
}
