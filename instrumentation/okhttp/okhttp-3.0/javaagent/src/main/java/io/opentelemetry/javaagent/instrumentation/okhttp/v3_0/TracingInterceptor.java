/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.okhttp.v3_0;

import static io.opentelemetry.javaagent.instrumentation.okhttp.v3_0.OkHttpClientTracer.tracer;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class TracingInterceptor implements Interceptor {

  @Override
  public Response intercept(Chain chain) throws IOException {
    Context parentContext = Context.current();
    if (!tracer().shouldStartOperation(parentContext)) {
      return chain.proceed(chain.request());
    }

    Request.Builder requestBuilder = chain.request().newBuilder();
    Context context = tracer().startOperation(parentContext, chain.request(), requestBuilder);

    Response response;
    try (Scope ignored = context.makeCurrent()) {
      response = chain.proceed(requestBuilder.build());
    } catch (Throwable t) {
      tracer().endExceptionally(context, t);
      throw t;
    }
    tracer().end(context, response);
    return response;
  }
}
