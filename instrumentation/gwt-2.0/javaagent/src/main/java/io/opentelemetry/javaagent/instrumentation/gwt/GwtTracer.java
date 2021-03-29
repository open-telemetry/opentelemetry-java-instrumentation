/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.gwt;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import java.lang.reflect.Method;

public class GwtTracer extends BaseTracer {
  private static final ContextKey<Object> RPC_CONTEXT_KEY =
      ContextKey.named("opentelemetry-gwt-rpc-context-key");

  private static final GwtTracer TRACER = new GwtTracer();

  public static GwtTracer tracer() {
    return TRACER;
  }

  private GwtTracer() {
    super(GlobalOpenTelemetry.get());
  }

  public Context startRpcSpan(Object target, Method method) {
    String spanName = spanNameForMethod(target.getClass(), method);
    Context context = super.startSpan(spanName);
    return context.with(RPC_CONTEXT_KEY, Boolean.TRUE);
  }

  public void endSpan(Context context, Throwable throwable) {
    if (throwable != null) {
      endExceptionally(context, throwable);
    } else {
      end(context);
    }
  }

  public void rpcFailure(Throwable throwable) {
    Context context = Context.current();
    if (context.get(RPC_CONTEXT_KEY) == null) {
      // not inside rpc invocation
      return;
    }

    tracer().onException(context, throwable);
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.gwt-2.0";
  }
}
