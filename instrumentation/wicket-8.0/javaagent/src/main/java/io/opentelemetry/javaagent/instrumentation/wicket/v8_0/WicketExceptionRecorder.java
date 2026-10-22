/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.wicket.v8_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.LocalRootSpan;
import io.opentelemetry.instrumentation.api.internal.HttpServerExceptionRecorder;
import io.opentelemetry.instrumentation.api.semconv.http.internal.HttpSchemaUrls;

public final class WicketExceptionRecorder {
  private static final HttpServerExceptionRecorder exceptionRecorder =
      HttpServerExceptionRecorder.create(
          GlobalOpenTelemetry.get(), "io.opentelemetry.wicket-8.0", HttpSchemaUrls.SCHEMA_URL);

  public static void record(Context context, Exception exception) {
    exceptionRecorder.recordException(
        context, LocalRootSpan.fromContextOrNull(context), WicketErrorUnwrapper.unwrap(exception));
  }

  private WicketExceptionRecorder() {}
}
