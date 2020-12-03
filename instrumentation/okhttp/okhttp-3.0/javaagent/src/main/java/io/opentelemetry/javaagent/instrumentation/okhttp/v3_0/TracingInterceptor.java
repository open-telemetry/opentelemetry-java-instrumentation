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
    if (!tracer().shouldStartSpan(parentContext)) {
      return chain.proceed(chain.request());
    }

    Request.Builder requestBuilder = chain.request().newBuilder();
    Context context = tracer().startSpan(parentContext, chain.request(), requestBuilder);

    Response response;
    try (Scope ignored = context.makeCurrent()) {
      response = chain.proceed(requestBuilder.build());
    } catch (Exception e) {
      tracer().endExceptionally(context, e);
      throw e;
    }
    tracer().end(context, response);
    return response;
  }
}
