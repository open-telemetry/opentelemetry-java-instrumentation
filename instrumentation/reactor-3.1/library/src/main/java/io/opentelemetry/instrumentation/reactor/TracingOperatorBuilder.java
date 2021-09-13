/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.reactor;

public final class TracingOperatorBuilder {
  private final ReactorAsyncOperationEndStrategyBuilder reactorAsyncOperationEndStrategyBuilder;

  TracingOperatorBuilder() {
    reactorAsyncOperationEndStrategyBuilder = ReactorAsyncOperationEndStrategy.newBuilder();
  }

  public TracingOperatorBuilder setCaptureExperimentalSpanAttributes(
      boolean captureExperimentalSpanAttributes) {
    reactorAsyncOperationEndStrategyBuilder.setCaptureExperimentalSpanAttributes(
        captureExperimentalSpanAttributes);
    return this;
  }

  public TracingOperatorBuilder setEmitCheckpoints(boolean emitCheckpoints) {
    reactorAsyncOperationEndStrategyBuilder.setEmitCheckpoints(emitCheckpoints);
    return this;
  }

  public TracingOperator build() {
    return new TracingOperator(reactorAsyncOperationEndStrategyBuilder.build());
  }
}
