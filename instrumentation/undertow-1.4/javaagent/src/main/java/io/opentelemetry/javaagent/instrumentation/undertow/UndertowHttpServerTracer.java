/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.undertow;

import static io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming.Source.CONTAINER;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.api.servlet.AppServerBridge;
import io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming;
import io.opentelemetry.instrumentation.api.tracer.HttpServerTracer;
import io.opentelemetry.javaagent.bootstrap.undertow.KeyHolder;
import io.opentelemetry.javaagent.bootstrap.undertow.UndertowActiveHandlers;
import io.undertow.server.DefaultResponseListener;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import java.net.InetSocketAddress;
import org.checkerframework.checker.nullness.qual.Nullable;

public class UndertowHttpServerTracer
    extends HttpServerTracer<
        HttpServerExchange, HttpServerExchange, HttpServerExchange, HttpServerExchange> {
  private static final UndertowHttpServerTracer TRACER = new UndertowHttpServerTracer();

  public static UndertowHttpServerTracer tracer() {
    return TRACER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.undertow-1.4";
  }

  public Context startServerSpan(HttpServerExchange exchange) {
    return startSpan(
        exchange, exchange, exchange, "HTTP " + exchange.getRequestMethod().toString());
  }

  @Override
  protected Context customizeContext(Context context, HttpServerExchange exchange) {
    context = ServerSpanNaming.init(context, CONTAINER);
    // span is ended when counter reaches 0, we start from 2 which accounts for the
    // handler that started the span and exchange completion listener
    context = UndertowActiveHandlers.init(context, 2);
    return AppServerBridge.init(context);
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
      endSpan(context, throwable, exchange);
    }
  }

  public void exchangeCompleted(Context context, HttpServerExchange exchange) {
    // after exchange is completed we can read response status
    // if all handlers have completed we can end the span, if there are running
    // handlers we'll end the span when last handler exits
    if (UndertowActiveHandlers.decrementAndGet(context) == 0) {
      Throwable throwable = exchange.getAttachment(DefaultResponseListener.EXCEPTION);
      endSpan(context, throwable, exchange);
    }
  }

  private static void endSpan(Context context, Throwable throwable, HttpServerExchange exchange) {
    if (throwable == null) {
      throwable = AppServerBridge.getException(context);
    }

    if (throwable != null) {
      tracer().endExceptionally(context, throwable, exchange);
    } else {
      tracer().end(context, exchange);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  @Nullable
  public Context getServerContext(HttpServerExchange exchange) {
    AttachmentKey<Context> contextKey =
        (AttachmentKey<Context>) KeyHolder.contextKeys.get(AttachmentKey.class);
    if (contextKey == null) {
      return null;
    }
    return exchange.getAttachment(contextKey);
  }

  @Override
  @Nullable
  protected Integer peerPort(HttpServerExchange exchange) {
    InetSocketAddress peerAddress =
        exchange.getConnection().getPeerAddress(InetSocketAddress.class);
    return peerAddress.getPort();
  }

  @Override
  @Nullable
  protected String peerHostIp(HttpServerExchange exchange) {
    InetSocketAddress peerAddress =
        exchange.getConnection().getPeerAddress(InetSocketAddress.class);
    return peerAddress.getHostString();
  }

  @Override
  protected String flavor(HttpServerExchange exchange, HttpServerExchange exchange2) {
    return exchange.getProtocol().toString();
  }

  @Override
  protected TextMapGetter<HttpServerExchange> getGetter() {
    return UndertowExchangeGetter.GETTER;
  }

  @Override
  protected String scheme(HttpServerExchange exchange) {
    return exchange.getRequestScheme();
  }

  @Override
  protected String host(HttpServerExchange exchange) {
    return exchange.getHostAndPort();
  }

  @Override
  protected String target(HttpServerExchange exchange) {
    String target = exchange.getRequestPath();
    String queryString = exchange.getQueryString();
    if (queryString != null && !queryString.isEmpty()) {
      target += "?" + queryString;
    }
    return target;
  }

  @Override
  protected String method(HttpServerExchange exchange) {
    return exchange.getRequestMethod().toString();
  }

  @Override
  @Nullable
  protected String requestHeader(HttpServerExchange exchange, String name) {
    return exchange.getRequestHeaders().getFirst(name);
  }

  @Override
  protected int responseStatus(HttpServerExchange exchange) {
    return exchange.getStatusCode();
  }

  @SuppressWarnings("unchecked")
  @Override
  protected void attachServerContext(Context context, HttpServerExchange exchange) {
    AttachmentKey<Context> contextKey =
        (AttachmentKey<Context>)
            KeyHolder.contextKeys.computeIfAbsent(
                AttachmentKey.class, key -> AttachmentKey.create(Context.class));
    exchange.putAttachment(contextKey, context);
  }
}
