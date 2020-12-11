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
    Context parentContext = Context.current();
    if (!tracer().shouldStartOperation(parentContext)) {
      return chain.proceed(chain.request());
    }

    Context context = tracer().startOperation(parentContext, chain.request());

    Response response;
    try (Scope ignored = context.makeCurrent()) {
      response = chain.proceed(chain.request());
    } catch (Throwable t) {
      tracer().endExceptionally(context, t);
      throw t;
    }

    tracer().end(context, response);
    return response;
  }
}
