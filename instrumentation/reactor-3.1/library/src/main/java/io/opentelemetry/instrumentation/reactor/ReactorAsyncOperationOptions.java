/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.reactor;

public final class ReactorAsyncOperationOptions {
  private final boolean captureExperimentalSpanAttributes;
  private final boolean emitCheckpoints;
  private final boolean traceMultipleSubscribers;

  ReactorAsyncOperationOptions(
      boolean captureExperimentalSpanAttributes,
      boolean emitCheckpoint,
      boolean traceMultipleSubscribers) {
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    this.emitCheckpoints = emitCheckpoint;
    this.traceMultipleSubscribers = traceMultipleSubscribers;
  }

  public boolean captureExperimentalSpanAttributes() {
    return captureExperimentalSpanAttributes;
  }

  public boolean emitCheckpoints() {
    return emitCheckpoints;
  }

  public boolean traceMultipleSubscribers() {
    return traceMultipleSubscribers;
  }
}
