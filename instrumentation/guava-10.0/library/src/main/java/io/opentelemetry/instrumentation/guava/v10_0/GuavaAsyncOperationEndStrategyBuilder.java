/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.guava.v10_0;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

public final class GuavaAsyncOperationEndStrategyBuilder {
  private boolean captureExperimentalSpanAttributes = false;

  GuavaAsyncOperationEndStrategyBuilder() {}

  /** Whether to record the experimental {@code guava.canceled} span attribute on cancellation. */
  @CanIgnoreReturnValue
  public GuavaAsyncOperationEndStrategyBuilder setCaptureExperimentalSpanAttributes(
      boolean captureExperimentalSpanAttributes) {
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    return this;
  }

  public GuavaAsyncOperationEndStrategy build() {
    return new GuavaAsyncOperationEndStrategy(captureExperimentalSpanAttributes);
  }
}
