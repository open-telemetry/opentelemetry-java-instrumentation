/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava2;

public final class RxJava2AsyncSpanEndStrategyBuilder {

  private boolean captureExperimentalSpanAttributes;

  RxJava2AsyncSpanEndStrategyBuilder() {}

  public RxJava2AsyncSpanEndStrategyBuilder setCaptureExperimentalSpanAttributes(
      boolean captureExperimentalSpanAttributes) {
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    return this;
  }

  public RxJava2AsyncSpanEndStrategy build() {
    return new RxJava2AsyncSpanEndStrategy(captureExperimentalSpanAttributes);
  }
}
