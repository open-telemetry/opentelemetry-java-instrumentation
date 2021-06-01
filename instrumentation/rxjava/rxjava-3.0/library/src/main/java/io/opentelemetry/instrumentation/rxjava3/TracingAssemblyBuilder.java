/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava3;

public final class TracingAssemblyBuilder {
  private boolean captureExperimentalSpanAttributes;

  TracingAssemblyBuilder() {}

  public TracingAssemblyBuilder setCaptureExperimentalSpanAttributes(
      boolean captureExperimentalSpanAttributes) {
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    return this;
  }

  public TracingAssembly build() {
    return new TracingAssembly(captureExperimentalSpanAttributes);
  }
}
