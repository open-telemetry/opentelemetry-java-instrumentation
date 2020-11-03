/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.okhttp.v3_0;

import static io.opentelemetry.javaagent.instrumentation.okhttp.v3_0.OkHttpClientTracer.tracer;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class TracingInterceptor implements Interceptor {

  @Override
  public Response intercept(Chain chain) throws IOException {
    Span span = tracer().startSpan(chain.request());

    Response response;
    Request.Builder requestBuilder = chain.request().newBuilder();
    try (Scope ignored = tracer().startScope(span, requestBuilder)) {
      response = chain.proceed(requestBuilder.build());
    } catch (Exception e) {
      tracer().endExceptionally(span, e);
      throw e;
    }
    tracer().end(span, response);
    return response;
  }
}
