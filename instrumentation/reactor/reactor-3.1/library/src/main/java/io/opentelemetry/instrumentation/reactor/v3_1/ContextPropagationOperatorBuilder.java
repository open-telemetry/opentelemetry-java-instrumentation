/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.reactor.v3_1;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

public final class ContextPropagationOperatorBuilder {
  private boolean captureExperimentalSpanAttributes;

  ContextPropagationOperatorBuilder() {}

  @CanIgnoreReturnValue
  public ContextPropagationOperatorBuilder setCaptureExperimentalSpanAttributes(
      boolean captureExperimentalSpanAttributes) {
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    return this;
  }

  public ContextPropagationOperator build() {
    return new ContextPropagationOperator(captureExperimentalSpanAttributes);
  }
}
