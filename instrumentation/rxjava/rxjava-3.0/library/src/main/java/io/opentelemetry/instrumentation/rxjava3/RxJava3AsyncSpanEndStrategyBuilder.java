/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava3;

public final class RxJava3AsyncSpanEndStrategyBuilder {

  private boolean captureExperimentalSpanAttributes;

  RxJava3AsyncSpanEndStrategyBuilder() {}

  public RxJava3AsyncSpanEndStrategyBuilder setCaptureExperimentalSpanAttributes(
      boolean captureExperimentalSpanAttributes) {
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    return this;
  }

  public RxJava3AsyncSpanEndStrategy build() {
    return new RxJava3AsyncSpanEndStrategy(captureExperimentalSpanAttributes);
  }
}
