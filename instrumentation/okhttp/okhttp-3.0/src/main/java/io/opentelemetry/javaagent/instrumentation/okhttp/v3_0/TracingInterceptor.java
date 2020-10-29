/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.okhttp.v3_0;

import static io.opentelemetry.javaagent.instrumentation.okhttp.v3_0.OkHttpClientTracer.TRACER;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class TracingInterceptor implements Interceptor {

  @Override
  public Response intercept(Chain chain) throws IOException {
    Span span = TRACER.startSpan(chain.request());

    Response response;
    Request.Builder requestBuilder = chain.request().newBuilder();
    try (Scope ignored = TRACER.startScope(span, requestBuilder)) {
      response = chain.proceed(requestBuilder.build());
    } catch (Exception e) {
      TRACER.endExceptionally(span, e);
      throw e;
    }
    TRACER.end(span, response);
    return response;
  }
}
