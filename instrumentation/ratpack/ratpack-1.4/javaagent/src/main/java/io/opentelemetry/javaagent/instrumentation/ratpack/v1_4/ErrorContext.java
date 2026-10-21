/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ratpack.v1_4;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.ErrorCauseExtractor;

public final class ErrorContext {

  private final Span span;

  public ErrorContext(Context context) {
    this.span = Span.fromContext(context);
  }

  public void recordException(Throwable error) {
    span.setStatus(StatusCode.ERROR);
    span.recordException(ErrorCauseExtractor.getDefault().extract(error));
  }
}
