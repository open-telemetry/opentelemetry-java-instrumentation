/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.okhttp.v3_0;

import static io.opentelemetry.javaagent.instrumentation.okhttp.v3_0.OkHttpClientTracer.tracer;

import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.HttpClientOperation;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class TracingInterceptor implements Interceptor {

  @Override
  public Response intercept(Chain chain) throws IOException {
    Request.Builder requestBuilder = chain.request().newBuilder();
    HttpClientOperation<Response> operation =
        tracer().startOperation(chain.request(), requestBuilder);

    Response response;
    try (Scope ignored = operation.makeCurrent()) {
      response = chain.proceed(requestBuilder.build());
    } catch (Throwable t) {
      operation.endExceptionally(t);
      throw t;
    }
    operation.end(response);
    return response;
  }
}
