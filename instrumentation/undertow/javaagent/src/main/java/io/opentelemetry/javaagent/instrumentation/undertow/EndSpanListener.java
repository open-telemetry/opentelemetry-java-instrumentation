/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.undertow;

import static io.opentelemetry.javaagent.instrumentation.undertow.UndertowHttpServerTracer.tracer;

import io.opentelemetry.context.Context;
import io.undertow.server.DefaultResponseListener;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpServerExchange;

public class EndSpanListener implements ExchangeCompletionListener {
  private final Context context;

  public EndSpanListener(Context context) {
    this.context = context;
  }

  @Override
  public void exchangeEvent(HttpServerExchange exchange, NextListener nextListener) {
    Throwable throwable = exchange.getAttachment(DefaultResponseListener.EXCEPTION);
    if (throwable != null) {
      tracer().endExceptionally(context, throwable, exchange);
    } else {
      tracer().end(context, exchange);
    }
    nextListener.proceed();
  }
}
