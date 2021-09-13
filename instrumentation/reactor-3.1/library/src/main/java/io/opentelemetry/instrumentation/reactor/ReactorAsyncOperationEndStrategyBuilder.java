/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.reactor;

public final class ReactorAsyncOperationEndStrategyBuilder {
  private boolean captureExperimentalSpanAttributes;
  private boolean emitCheckpoints;

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

  public ReactorAsyncOperationEndStrategy build() {
    return new ReactorAsyncOperationEndStrategy(captureExperimentalSpanAttributes, emitCheckpoints);
  }
}
