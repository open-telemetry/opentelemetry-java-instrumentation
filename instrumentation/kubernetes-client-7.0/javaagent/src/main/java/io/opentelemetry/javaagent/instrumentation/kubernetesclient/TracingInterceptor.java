/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kubernetesclient;

import static io.opentelemetry.javaagent.instrumentation.kubernetesclient.KubernetesClientTracer.tracer;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Response;

public class TracingInterceptor implements Interceptor {

  @Override
  public Response intercept(Chain chain) throws IOException {

    Context context = tracer().startSpan(Context.current(), chain.request());
    tracer()
        .onRequest(
            io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge.spanFromContext(
                context),
            chain.request());

    Response response;
    try (Scope ignored = context.makeCurrent()) {
      response = chain.proceed(chain.request());
    } catch (Exception e) {
      tracer().endExceptionally(context, e);
      throw e;
    }

    tracer().end(context, response);
    return response;
  }
}
