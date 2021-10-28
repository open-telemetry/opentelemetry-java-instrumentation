/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.okhttp.v2_2;

import com.squareup.okhttp.Interceptor;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.io.IOException;

public class TracingInterceptor implements Interceptor {
  private final Instrumenter<Request, Response> instrumenter;
  private final ContextPropagators propagators;

  public TracingInterceptor(
      Instrumenter<Request, Response> instrumenter, ContextPropagators propagators) {
    this.instrumenter = instrumenter;
    this.propagators = propagators;
  }

  @Override
  public Response intercept(Chain chain) throws IOException {
    Context parentContext = Context.current();
    Request request = chain.request();

    if (!instrumenter.shouldStart(parentContext, request)) {
      return chain.proceed(request);
    }

    Context context = instrumenter.start(parentContext, request);
    request = injectContextToRequest(request, context);

    Response response;
    try (Scope ignored = context.makeCurrent()) {
      response = chain.proceed(request);
    } catch (Exception e) {
      instrumenter.end(context, request, null, e);
      throw e;
    }
    instrumenter.end(context, request, response, null);
    return response;
  }

  // Request is immutable so we need to create a new request when injecting context propagation
  // headers
  private Request injectContextToRequest(Request request, Context context) {
    Request.Builder requestBuilder = request.newBuilder();
    propagators
        .getTextMapPropagator()
        .inject(context, requestBuilder, RequestBuilderHeaderSetter.INSTANCE);
    return requestBuilder.build();
  }
}
