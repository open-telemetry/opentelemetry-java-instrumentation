/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.guava;

public final class GuavaAsyncSpanEndStrategyBuilder {
  private boolean captureExperimentalSpanAttributes = false;

  GuavaAsyncSpanEndStrategyBuilder() {}

  public GuavaAsyncSpanEndStrategyBuilder setCaptureExperimentalSpanAttributes(
      boolean captureExperimentalSpanAttributes) {
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    return this;
  }

  public GuavaAsyncSpanEndStrategy build() {
    return new GuavaAsyncSpanEndStrategy(captureExperimentalSpanAttributes);
  }
}
