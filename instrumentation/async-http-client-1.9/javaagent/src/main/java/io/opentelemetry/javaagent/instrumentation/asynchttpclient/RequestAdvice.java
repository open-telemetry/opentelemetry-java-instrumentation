/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient;

import static io.opentelemetry.javaagent.instrumentation.asynchttpclient.AsyncHttpClientTracer.tracer;

import com.ning.http.client.AsyncHandler;
import com.ning.http.client.Request;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.HttpClientOperation;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import net.bytebuddy.asm.Advice;

public class RequestAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(
      @Advice.Argument(0) Request request,
      @Advice.Argument(1) AsyncHandler<?> handler,
      @Advice.Local("otelScope") Scope scope) {
    HttpClientOperation operation = tracer().startOperation(request);
    InstrumentationContext.get(AsyncHandler.class, HttpClientOperation.class)
        .put(handler, operation);
    scope = operation.makeCurrent();
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void onExit(@Advice.Local("otelScope") Scope scope) {
    scope.close();
    // span ended in ClientResponseAdvice
  }
}
