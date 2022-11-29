/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava.v3_0;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

public final class TracingAssemblyBuilder {
  private boolean captureExperimentalSpanAttributes;

  TracingAssemblyBuilder() {}

  @CanIgnoreReturnValue
  public TracingAssemblyBuilder setCaptureExperimentalSpanAttributes(
      boolean captureExperimentalSpanAttributes) {
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    return this;
  }

  public TracingAssembly build() {
    return new TracingAssembly(captureExperimentalSpanAttributes);
  }
}
