/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.okhttp.v2_2;

import static io.opentelemetry.javaagent.instrumentation.okhttp.v2_2.OkHttpClientTracer.tracer;

import com.squareup.okhttp.Interceptor;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.io.IOException;

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
