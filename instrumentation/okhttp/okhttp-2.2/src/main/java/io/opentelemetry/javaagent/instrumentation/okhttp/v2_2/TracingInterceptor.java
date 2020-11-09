/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.okhttp.v2_2;

import static io.opentelemetry.javaagent.instrumentation.okhttp.v2_2.OkHttpClientTracer.tracer;

import com.squareup.okhttp.Interceptor;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import java.io.IOException;

public class TracingInterceptor implements Interceptor {
  @Override
  public Response intercept(Chain chain) throws IOException {
    Span span = tracer().startSpan(chain.request());
    Request.Builder requestBuilder = chain.request().newBuilder();

    Response response;
    try (Scope scope = tracer().startScope(span, requestBuilder)) {
      response = chain.proceed(requestBuilder.build());
    } catch (Exception e) {
      tracer().endExceptionally(span, e);
      throw e;
    }
    tracer().end(span, response);
    return response;
  }
}
