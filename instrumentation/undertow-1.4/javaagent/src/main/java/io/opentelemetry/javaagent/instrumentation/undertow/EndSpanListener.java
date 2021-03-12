/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.undertow;

import static io.opentelemetry.javaagent.instrumentation.undertow.UndertowHttpServerTracer.tracer;

import io.opentelemetry.context.Context;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpServerExchange;

public class EndSpanListener implements ExchangeCompletionListener {
  private final Context context;

  public EndSpanListener(Context context) {
    this.context = context;
  }

  @Override
  public void exchangeEvent(HttpServerExchange exchange, NextListener nextListener) {
    tracer().exchangeCompleted(context, exchange);
    nextListener.proceed();
  }
}
