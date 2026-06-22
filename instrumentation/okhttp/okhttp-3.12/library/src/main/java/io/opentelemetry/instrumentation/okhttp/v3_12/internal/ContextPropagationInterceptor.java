/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.okhttp.v3_12.internal;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Network interceptor that injects the client span context, created by {@link
 * TracingEventListener}, into outgoing request headers. Header injection cannot happen inside the
 * {@link TracingEventListener} because the okhttp {@code EventListener} callbacks may not mutate
 * the request.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class ContextPropagationInterceptor implements Interceptor {

  private final ContextPropagators propagators;

  public ContextPropagationInterceptor(ContextPropagators propagators) {
    this.propagators = propagators;
  }

  @Override
  public Response intercept(Chain chain) throws IOException {
    Request request = chain.request();
    OkHttpClientCallState state = OkHttpClientCallState.get(chain.call());
    Context context = state != null ? state.context() : null;
    if (context == null) {
      return chain.proceed(request);
    }

    Request.Builder requestBuilder = request.newBuilder();
    propagators
        .getTextMapPropagator()
        .inject(context, requestBuilder, RequestHeaderSetter.INSTANCE);
    try (Scope ignored = context.makeCurrent()) {
      return chain.proceed(requestBuilder.build());
    }
  }
}
