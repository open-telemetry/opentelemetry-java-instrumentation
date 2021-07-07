/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava2;

public final class RxJava2AsyncOperationEndStrategyBuilder {

  private boolean captureExperimentalSpanAttributes;

  RxJava2AsyncOperationEndStrategyBuilder() {}

  public RxJava2AsyncOperationEndStrategyBuilder setCaptureExperimentalSpanAttributes(
      boolean captureExperimentalSpanAttributes) {
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    return this;
  }

  public RxJava2AsyncOperationEndStrategy build() {
    return new RxJava2AsyncOperationEndStrategy(captureExperimentalSpanAttributes);
  }
}
