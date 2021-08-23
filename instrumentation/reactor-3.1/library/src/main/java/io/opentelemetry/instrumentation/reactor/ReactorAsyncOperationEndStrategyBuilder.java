/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.reactor;

public final class ReactorAsyncOperationEndStrategyBuilder {
  private boolean captureExperimentalSpanAttributes;
  private boolean emitCheckpoints;
  private boolean traceMultipleSubscribers;

  ReactorAsyncOperationEndStrategyBuilder() {}

  public ReactorAsyncOperationEndStrategyBuilder setCaptureExperimentalSpanAttributes(
      boolean captureExperimentalSpanAttributes) {
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    return this;
  }

  public ReactorAsyncOperationEndStrategyBuilder setEmitCheckpoints(boolean emitCheckpoints) {
    this.emitCheckpoints = emitCheckpoints;
    return this;
  }

  public ReactorAsyncOperationEndStrategyBuilder setTraceMultipleSubscribers(
      boolean traceMultipleSubscribers) {
    this.traceMultipleSubscribers = traceMultipleSubscribers;
    return this;
  }

  public ReactorAsyncOperationEndStrategy build() {
    ReactorAsyncOperationOptions options =
        new ReactorAsyncOperationOptions(
            captureExperimentalSpanAttributes, emitCheckpoints, traceMultipleSubscribers);
    return new ReactorAsyncOperationEndStrategy(options);
  }
}
