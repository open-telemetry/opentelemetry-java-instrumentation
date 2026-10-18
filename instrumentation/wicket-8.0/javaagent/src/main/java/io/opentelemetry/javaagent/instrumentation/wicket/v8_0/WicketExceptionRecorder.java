/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.wicket.v8_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.LocalRootSpan;
import io.opentelemetry.instrumentation.api.internal.HttpServerExceptionRecorder;
import io.opentelemetry.instrumentation.api.internal.LoggerUtil;
import io.opentelemetry.semconv.SchemaUrls;

public final class WicketExceptionRecorder {
  private static final Logger logger =
      LoggerUtil.createLogger(
          GlobalOpenTelemetry.get(), "io.opentelemetry.wicket-8.0", SchemaUrls.V1_41_0);

  public static void record(Context context, Exception exception) {
    HttpServerExceptionRecorder.recordException(
        context,
        LocalRootSpan.fromContextOrNull(context),
        exception,
        WicketErrorUnwrapper::unwrap,
        logger);
  }

  private WicketExceptionRecorder() {}
}
