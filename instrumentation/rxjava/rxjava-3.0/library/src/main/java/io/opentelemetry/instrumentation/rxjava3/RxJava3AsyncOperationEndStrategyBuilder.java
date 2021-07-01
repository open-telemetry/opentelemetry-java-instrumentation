/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava3;

public final class RxJava3AsyncOperationEndStrategyBuilder {

  private boolean captureExperimentalSpanAttributes;

  RxJava3AsyncOperationEndStrategyBuilder() {}

  public RxJava3AsyncOperationEndStrategyBuilder setCaptureExperimentalSpanAttributes(
      boolean captureExperimentalSpanAttributes) {
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    return this;
  }

  public RxJava3AsyncOperationEndStrategy build() {
    return new RxJava3AsyncOperationEndStrategy(captureExperimentalSpanAttributes);
  }
}
