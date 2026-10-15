/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.wicket.v8_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.internal.HttpServerExceptionRecorder;

public final class WicketExceptionRecorder {

  private static final HttpServerExceptionRecorder serverExceptionRecorder =
      HttpServerExceptionRecorder.create(GlobalOpenTelemetry.get(), "io.opentelemetry.wicket-8.0");

  public static void recordException(Context context, Span span, Throwable throwable) {
    serverExceptionRecorder.recordException(context, span, throwable);
  }

  private WicketExceptionRecorder() {}
}
