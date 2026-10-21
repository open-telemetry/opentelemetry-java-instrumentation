/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ratpack.v1_4;

import io.opentelemetry.context.Context;

/**
 * Provides a dedicated Ratpack registry key for the span that receives request errors, keeping
 * error reporting separate from the {@link Context} entry used for HTTP client parent selection.
 */
public final class ErrorContext {

  private final Context context;

  public ErrorContext(Context context) {
    this.context = context;
  }

  public void recordException(Throwable error) {
    RatpackSingletons.onError(context, error);
  }
}
