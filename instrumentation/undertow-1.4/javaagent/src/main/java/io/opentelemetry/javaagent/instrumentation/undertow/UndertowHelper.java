/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.undertow;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.servlet.AppServerBridge;
import io.opentelemetry.javaagent.bootstrap.undertow.KeyHolder;
import io.opentelemetry.javaagent.bootstrap.undertow.UndertowActiveHandlers;
import io.undertow.server.DefaultResponseListener;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import javax.annotation.Nullable;

public class UndertowHelper {
  private final Instrumenter<HttpServerExchange, HttpServerExchange> instrumenter;

  UndertowHelper(Instrumenter<HttpServerExchange, HttpServerExchange> instrumenter) {
    this.instrumenter = instrumenter;
  }

  public boolean shouldStart(Context parentContext, HttpServerExchange exchange) {
    return instrumenter.shouldStart(parentContext, exchange);
  }

  public Context start(Context parentContext, HttpServerExchange exchange) {
    Context context = instrumenter.start(parentContext, exchange);
    attachServerContext(context, exchange);

    return context;
  }

  public void end(Context context, HttpServerExchange exchange, @Nullable Throwable error) {
    if (error == null) {
      error = AppServerBridge.getException(context);
    }

    instrumenter.end(context, exchange, exchange, error);
  }

  public void handlerStarted(Context context) {
    // request was dispatched to a new thread, handler on the original thread
    // may exit before this one so we need to wait for this handler to complete
    // before ending span
    UndertowActiveHandlers.increment(context);
  }

  public void handlerCompleted(Context context, Throwable throwable, HttpServerExchange exchange) {
    // end the span when this is the last handler to complete and exchange has
    // been completed
    if (UndertowActiveHandlers.decrementAndGet(context) == 0) {
      end(context, exchange, throwable);
    }
  }

  public void exchangeCompleted(Context context, HttpServerExchange exchange) {
    // after exchange is completed we can read response status
    // if all handlers have completed we can end the span, if there are running
    // handlers we'll end the span when last handler exits
    if (UndertowActiveHandlers.decrementAndGet(context) == 0) {
      Throwable throwable = exchange.getAttachment(DefaultResponseListener.EXCEPTION);
      end(context, exchange, throwable);
    }
  }

  @SuppressWarnings("unchecked")
  @Nullable
  public Context getServerContext(HttpServerExchange exchange) {
    AttachmentKey<Context> contextKey =
        (AttachmentKey<Context>) KeyHolder.contextKeys.get(AttachmentKey.class);
    if (contextKey == null) {
      return null;
    }
    return exchange.getAttachment(contextKey);
  }

  @SuppressWarnings("unchecked")
  private static void attachServerContext(Context context, HttpServerExchange exchange) {
    AttachmentKey<Context> contextKey =
        (AttachmentKey<Context>)
            KeyHolder.contextKeys.computeIfAbsent(
                AttachmentKey.class, key -> AttachmentKey.create(Context.class));
    exchange.putAttachment(contextKey, context);
  }
}
