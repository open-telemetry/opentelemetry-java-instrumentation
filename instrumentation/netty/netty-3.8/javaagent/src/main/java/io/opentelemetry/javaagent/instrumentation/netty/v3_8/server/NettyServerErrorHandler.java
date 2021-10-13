/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.server;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.ErrorCauseExtractor;

public final class NettyServerErrorHandler {

  // copied from BaseTracer#onException()
  public static void onError(Context context, Throwable error) {
    Span span = Span.fromContext(context);
    span.setStatus(StatusCode.ERROR);
    span.recordException(ErrorCauseExtractor.jdk().extractCause(error));
  }

  private NettyServerErrorHandler() {}
}
