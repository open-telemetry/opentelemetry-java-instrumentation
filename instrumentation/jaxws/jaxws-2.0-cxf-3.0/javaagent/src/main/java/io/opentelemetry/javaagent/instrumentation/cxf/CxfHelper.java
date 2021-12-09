/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cxf;

import static io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming.Source.CONTROLLER;
import static io.opentelemetry.javaagent.instrumentation.cxf.CxfSingletons.instrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.Message;

public final class CxfHelper {
  private static final String REQUEST_KEY = CxfHelper.class.getName() + ".Request";
  private static final String CONTEXT_KEY = CxfHelper.class.getName() + ".Context";
  private static final String SCOPE_KEY = CxfHelper.class.getName() + ".Scope";

  private CxfHelper() {}

  public static void start(Message message) {
    Context parentContext = Context.current();

    CxfRequest request = new CxfRequest(message);

    if (!request.shouldCreateSpan()) {
      return;
    }

    ServerSpanNaming.updateServerSpanName(
        parentContext, CONTROLLER, CxfServerSpanNaming.SERVER_SPAN_NAME, request);

    if (!instrumenter().shouldStart(parentContext, request)) {
      return;
    }

    Context context = instrumenter().start(parentContext, request);
    Scope scope = context.makeCurrent();

    Exchange exchange = message.getExchange();
    exchange.put(REQUEST_KEY, request);
    exchange.put(CONTEXT_KEY, context);
    exchange.put(SCOPE_KEY, scope);
  }

  public static void end(Message message) {
    Exchange exchange = message.getExchange();
    Scope scope = (Scope) exchange.remove(SCOPE_KEY);
    if (scope == null) {
      return;
    }
    scope.close();

    CxfRequest request = (CxfRequest) exchange.remove(REQUEST_KEY);
    Context context = (Context) exchange.remove(CONTEXT_KEY);

    Throwable throwable = message.getContent(Exception.class);
    if (throwable instanceof Fault && throwable.getCause() != null) {
      throwable = throwable.getCause();
    }
    instrumenter().end(context, request, null, throwable);
  }
}
