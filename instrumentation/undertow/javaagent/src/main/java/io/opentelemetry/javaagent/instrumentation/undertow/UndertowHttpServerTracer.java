/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.undertow;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.api.servlet.AppServerBridge;
import io.opentelemetry.instrumentation.api.servlet.ServletSpanNaming;
import io.opentelemetry.instrumentation.api.tracer.HttpServerTracer;
import io.opentelemetry.javaagent.instrumentation.api.undertow.KeyHolder;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import java.lang.reflect.Method;
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
    return "io.opentelemetry.javaagent.undertow";
  }

  public Context startServerSpan(HttpServerExchange exchange, Method instrumentedMethod) {
    return startSpan(
        exchange, exchange, exchange, "HTTP " + exchange.getRequestMethod().toString());
  }

  @Override
  protected Context customizeContext(Context context, HttpServerExchange exchange) {
    context = ServletSpanNaming.init(context);
    return AppServerBridge.init(context);
  }

  @SuppressWarnings("unchecked")
  @Override
  public @Nullable Context getServerContext(HttpServerExchange exchange) {
    AttachmentKey<Context> contextKey =
        (AttachmentKey<Context>) KeyHolder.contextKeys.get(AttachmentKey.class);
    if (contextKey == null) {
      return null;
    }
    return exchange.getAttachment(contextKey);
  }

  @Override
  protected @Nullable Integer peerPort(HttpServerExchange exchange) {
    InetSocketAddress peerAddress =
        exchange.getConnection().getPeerAddress(InetSocketAddress.class);
    return peerAddress.getPort();
  }

  @Override
  protected @Nullable String peerHostIP(HttpServerExchange exchange) {
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
  protected String url(HttpServerExchange exchange) {
    String result = exchange.getRequestURL();
    if (exchange.getQueryString() == null || exchange.getQueryString().isEmpty()) {
      return result;
    } else {
      return result + "?" + exchange.getQueryString();
    }
  }

  @Override
  protected String method(HttpServerExchange exchange) {
    return exchange.getRequestMethod().toString();
  }

  @Override
  protected @Nullable String requestHeader(HttpServerExchange exchange, String name) {
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
