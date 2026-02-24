/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava.v3.common;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

public final class RxJava3AsyncOperationEndStrategyBuilder {

  private boolean captureExperimentalSpanAttributes;

  RxJava3AsyncOperationEndStrategyBuilder() {}

  @CanIgnoreReturnValue
  public RxJava3AsyncOperationEndStrategyBuilder setCaptureExperimentalSpanAttributes(
      boolean captureExperimentalSpanAttributes) {
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    return this;
  }

  public RxJava3AsyncOperationEndStrategy build() {
    return new RxJava3AsyncOperationEndStrategy(captureExperimentalSpanAttributes);
  }
}
