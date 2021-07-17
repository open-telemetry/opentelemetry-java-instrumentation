/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.guava;

public final class GuavaAsyncOperationEndStrategyBuilder {
  private boolean captureExperimentalSpanAttributes = false;

  GuavaAsyncOperationEndStrategyBuilder() {}

  public GuavaAsyncOperationEndStrategyBuilder setCaptureExperimentalSpanAttributes(
      boolean captureExperimentalSpanAttributes) {
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    return this;
  }

  public GuavaAsyncOperationEndStrategy build() {
    return new GuavaAsyncOperationEndStrategy(captureExperimentalSpanAttributes);
  }
}
