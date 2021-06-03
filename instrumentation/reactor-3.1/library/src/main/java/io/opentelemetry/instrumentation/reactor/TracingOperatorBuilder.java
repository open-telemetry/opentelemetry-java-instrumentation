/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.reactor;

public final class TracingOperatorBuilder {
  private boolean captureExperimentalSpanAttributes;

  TracingOperatorBuilder() {}

  public TracingOperatorBuilder setCaptureExperimentalSpanAttributes(
      boolean captureExperimentalSpanAttributes) {
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    return this;
  }

  public TracingOperator build() {
    return new TracingOperator(captureExperimentalSpanAttributes);
  }
}
