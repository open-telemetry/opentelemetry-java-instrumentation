/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.reactor;

public final class TracingOperatorBuilder {
  private final ReactorAsyncOperationEndStrategyBuilder asyncOperationEndStrategyBuilder;

  TracingOperatorBuilder() {
    asyncOperationEndStrategyBuilder = ReactorAsyncOperationEndStrategy.newBuilder();
  }

  public TracingOperatorBuilder setCaptureExperimentalSpanAttributes(
      boolean captureExperimentalSpanAttributes) {
    asyncOperationEndStrategyBuilder.setCaptureExperimentalSpanAttributes(
        captureExperimentalSpanAttributes);
    return this;
  }

  public TracingOperatorBuilder setEmitCheckpoints(boolean emitCheckpoints) {
    asyncOperationEndStrategyBuilder.setEmitCheckpoints(emitCheckpoints);
    return this;
  }

  public TracingOperatorBuilder setTraceMultipleSubscribers(boolean traceMultipleSubscribers) {
    asyncOperationEndStrategyBuilder.setTraceMultipleSubscribers(traceMultipleSubscribers);
    return this;
  }

  public TracingOperator build() {
    return new TracingOperator(asyncOperationEndStrategyBuilder.build());
  }
}
