/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kubernetesclient;

import static io.opentelemetry.javaagent.instrumentation.kubernetesclient.KubernetesClientTracer.tracer;

import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.Operation;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Response;

public class TracingInterceptor implements Interceptor {

  @Override
  public Response intercept(Chain chain) throws IOException {
    Operation operation = tracer().startOperation(chain.request());

    Response response;
    try (Scope ignored = operation.makeCurrent()) {
      response = chain.proceed(chain.request());
    } catch (Throwable t) {
      tracer().endExceptionally(operation, t);
      throw t;
    }

    tracer().end(operation, response);
    return response;
  }
}
