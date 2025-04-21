/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.reactor.v3_1;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

public final class ReactorAsyncEndStrategyBuilder {
  private boolean captureExperimentalSpanAttributes;

  ReactorAsyncEndStrategyBuilder() {}

  @CanIgnoreReturnValue
  public ReactorAsyncEndStrategyBuilder setCaptureExperimentalSpanAttributes(
      boolean captureExperimentalSpanAttributes) {
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    return this;
  }

  public ReactorAsyncEndStrategy build() {
    return new ReactorAsyncEndStrategy(captureExperimentalSpanAttributes);
  }
}
