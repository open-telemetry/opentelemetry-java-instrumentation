/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.reactor;

public final class ReactorAsyncSpanEndStrategyBuilder {
  private boolean captureExperimentalSpanAttributes;

  ReactorAsyncSpanEndStrategyBuilder() {}

  public ReactorAsyncSpanEndStrategyBuilder setCaptureExperimentalSpanAttributes(
      boolean captureExperimentalSpanAttributes) {
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    return this;
  }

  public ReactorAsyncSpanEndStrategy build() {
    return new ReactorAsyncSpanEndStrategy(captureExperimentalSpanAttributes);
  }
}
