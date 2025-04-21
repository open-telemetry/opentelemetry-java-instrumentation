/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava.v2_0;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

public final class RxJava2AsyncEndStrategyBuilder {

  private boolean captureExperimentalSpanAttributes;

  RxJava2AsyncEndStrategyBuilder() {}

  @CanIgnoreReturnValue
  public RxJava2AsyncEndStrategyBuilder setCaptureExperimentalSpanAttributes(
      boolean captureExperimentalSpanAttributes) {
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    return this;
  }

  public RxJava2AsyncEndStrategy build() {
    return new RxJava2AsyncEndStrategy(captureExperimentalSpanAttributes);
  }
}
