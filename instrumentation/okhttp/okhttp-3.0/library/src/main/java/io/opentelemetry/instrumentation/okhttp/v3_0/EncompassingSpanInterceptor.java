/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.okhttp.v3_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

final class EncompassingSpanInterceptor implements Interceptor {

  final Instrumenter<Request, Void> encompassingInstrumenter;

  EncompassingSpanInterceptor() {
    encompassingInstrumenter =
        Instrumenter.<Request, Void>builder(
                GlobalOpenTelemetry.get(),
                "io.opentelemetry.okhttp-3.0",
                rq -> "okhttp3.Call.execute()")
            .buildInstrumenter();
  }

  @Override
  public Response intercept(Chain chain) throws IOException {
    Context parentContext = Context.current();
    Request request = chain.request();
    if (!encompassingInstrumenter.shouldStart(parentContext, request)) {
      return chain.proceed(request);
    }

    Context context = encompassingInstrumenter.start(parentContext, request);
    Throwable error = null;
    try (Scope ignored = context.makeCurrent()) {
      return chain.proceed(request);
    } catch (Throwable t) {
      error = t;
      throw t;
    } finally {
      encompassingInstrumenter.end(context, request, null, error);
    }
  }
}
