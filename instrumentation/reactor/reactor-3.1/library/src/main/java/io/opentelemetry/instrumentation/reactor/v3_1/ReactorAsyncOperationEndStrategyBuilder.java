/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.reactor.v3_1;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

public final class ReactorAsyncOperationEndStrategyBuilder {
  private boolean captureExperimentalSpanAttributes;

  ReactorAsyncOperationEndStrategyBuilder() {}

  @CanIgnoreReturnValue
  public ReactorAsyncOperationEndStrategyBuilder setCaptureExperimentalSpanAttributes(
      boolean captureExperimentalSpanAttributes) {
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    return this;
  }

  public ReactorAsyncOperationEndStrategy build() {
    return new ReactorAsyncOperationEndStrategy(captureExperimentalSpanAttributes);
  }
}
