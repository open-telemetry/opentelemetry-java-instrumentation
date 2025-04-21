/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava.v3.common;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

public final class RxJava3AsyncEndStrategyBuilder {

  private boolean captureExperimentalSpanAttributes;

  RxJava3AsyncEndStrategyBuilder() {}

  @CanIgnoreReturnValue
  public RxJava3AsyncEndStrategyBuilder setCaptureExperimentalSpanAttributes(
      boolean captureExperimentalSpanAttributes) {
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    return this;
  }

  public RxJava3AsyncEndStrategy build() {
    return new RxJava3AsyncEndStrategy(captureExperimentalSpanAttributes);
  }
}
