/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.reactor;

public final class ContextPropagationOperatorBuilder {
  private boolean captureExperimentalSpanAttributes;

  ContextPropagationOperatorBuilder() {}

  public ContextPropagationOperatorBuilder setCaptureExperimentalSpanAttributes(
      boolean captureExperimentalSpanAttributes) {
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    return this;
  }

  public ContextPropagationOperator build() {
    return new ContextPropagationOperator(captureExperimentalSpanAttributes);
  }
}
