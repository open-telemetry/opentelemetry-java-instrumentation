/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.okhttp.v2_2;

import static io.opentelemetry.javaagent.instrumentation.okhttp.v2_2.OkHttpClientTracer.TRACER;

import com.squareup.okhttp.Interceptor;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import java.io.IOException;

public class TracingInterceptor implements Interceptor {
  @Override
  public Response intercept(Chain chain) throws IOException {
    Span span = TRACER.startSpan(chain.request());
    Request.Builder requestBuilder = chain.request().newBuilder();

    Response response;
    try (Scope scope = TRACER.startScope(span, requestBuilder)) {
      response = chain.proceed(requestBuilder.build());
    } catch (Exception e) {
      TRACER.endExceptionally(span, e);
      throw e;
    }
    TRACER.end(span, response);
    return response;
  }
}
