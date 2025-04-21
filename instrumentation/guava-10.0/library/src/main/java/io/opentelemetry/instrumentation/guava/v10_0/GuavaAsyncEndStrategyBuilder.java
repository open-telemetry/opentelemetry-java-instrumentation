/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.guava.v10_0;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

public final class GuavaAsyncEndStrategyBuilder {
  private boolean captureExperimentalSpanAttributes = false;

  GuavaAsyncEndStrategyBuilder() {}

  @CanIgnoreReturnValue
  public GuavaAsyncEndStrategyBuilder setCaptureExperimentalSpanAttributes(
      boolean captureExperimentalSpanAttributes) {
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    return this;
  }

  public GuavaAsyncEndStrategy build() {
    return new GuavaAsyncEndStrategy(captureExperimentalSpanAttributes);
  }
}
