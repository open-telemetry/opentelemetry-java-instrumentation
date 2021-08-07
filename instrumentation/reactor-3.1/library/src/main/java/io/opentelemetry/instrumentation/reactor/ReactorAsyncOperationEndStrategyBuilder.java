/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.reactor;

public final class ReactorAsyncOperationEndStrategyBuilder {
  private boolean captureExperimentalSpanAttributes;

  ReactorAsyncOperationEndStrategyBuilder() {}

  public ReactorAsyncOperationEndStrategyBuilder setCaptureExperimentalSpanAttributes(
      boolean captureExperimentalSpanAttributes) {
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    return this;
  }

  public ReactorAsyncOperationEndStrategy build() {
    return new ReactorAsyncOperationEndStrategy(captureExperimentalSpanAttributes);
  }
}
