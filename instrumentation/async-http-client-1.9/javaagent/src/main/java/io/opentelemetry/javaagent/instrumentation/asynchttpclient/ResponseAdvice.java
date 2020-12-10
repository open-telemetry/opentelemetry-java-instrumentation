/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient;

import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.AsyncHandler;
import com.ning.http.client.Response;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.HttpClientOperation;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import net.bytebuddy.asm.Advice;

public class ResponseAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static Scope onEnter(
      @Advice.This AsyncCompletionHandler<?> handler, @Advice.Argument(0) Response response) {

    @SuppressWarnings("rawtypes")
    ContextStore<AsyncHandler, HttpClientOperation> contextStore =
        InstrumentationContext.get(AsyncHandler.class, HttpClientOperation.class);
    HttpClientOperation<Response> operation = contextStore.get(handler);
    if (operation == null) {
      return Scope.noop();
    }
    contextStore.put(handler, null);
    operation.end(response);
    return operation.makeParentCurrent();
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void onExit(@Advice.Enter Scope scope) {
    scope.close();
  }
}
