/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.okhttp.v3_0;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

final class TracingInterceptor implements Interceptor {

  private final Instrumenter<Request, Response> instrumenter;
  private final ContextPropagators propagators;

  TracingInterceptor(Instrumenter<Request, Response> instrumenter, ContextPropagators propagators) {
    this.instrumenter = instrumenter;
    this.propagators = propagators;
  }

  @Override
  public Response intercept(Chain chain) throws IOException {
    Request request = chain.request();
    Context parentContext = TracingCallFactory.getCallingContextForRequest(request);
    if (parentContext == null) {
      parentContext = Context.current();
    }

    if (!instrumenter.shouldStart(parentContext, request)) {
      return chain.proceed(chain.request());
    }

    TryInfo info = TryInfo.getTryInfo(parentContext);

    Context context = instrumenter.start(parentContext, request);
    Span span = Span.fromContext(context);
    span.setAttribute("http.retry_count", info.getTryCount());
    info.tryDone(span.getSpanContext());

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

  // Context injection is being handled manually for a reason: we want to use the OkHttp Request
  // type for additional AttributeExtractors provided by the user of this library
  // thus we must use Instrumenter<Request, Response>, and Request is immutable
  private Request injectContextToRequest(Request request, Context context) {
    Request.Builder requestBuilder = request.newBuilder();
    propagators.getTextMapPropagator().inject(context, requestBuilder, RequestHeaderSetter.SETTER);
    return requestBuilder.build();
  }

  static class TryInfo {

    private static final ContextKey TRY_INFO_KEY = ContextKey.named("try");
    private static final TryInfo NONE = new TryInfo();

    public static TryInfo getTryInfo(Context context) {
      Object info = context.get(TRY_INFO_KEY);
      if (info == null || !(info instanceof TryInfo)) {
        return NONE;
      }

      return (TryInfo) info;
    }

    public void tryDone(SpanContext newContext) {
      spanContext = newContext;
      tryCount++;
    }

    private SpanContext spanContext;
    private int tryCount;

    private TryInfo() {
      spanContext = SpanContext.getInvalid();
      tryCount = 0;
    }

    public static Context newInfo(Context context) {
      return context.with(TRY_INFO_KEY, new TryInfo());
    }

    public SpanContext getSpanContext() {
      return spanContext;
    }

    public int getTryCount() {
      return tryCount;
    }
  }
}
