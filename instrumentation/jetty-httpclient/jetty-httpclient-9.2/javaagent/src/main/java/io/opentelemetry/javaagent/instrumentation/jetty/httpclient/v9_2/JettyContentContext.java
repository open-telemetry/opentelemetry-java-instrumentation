/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v9_2;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import javax.annotation.Nullable;
import org.eclipse.jetty.client.HttpContent;
import org.eclipse.jetty.client.HttpExchange;
import org.eclipse.jetty.client.api.Request;

public final class JettyContentContext implements Context {

  private static final VirtualField<HttpContent, JettyContentContext> CONTENT_CONTEXT =
      VirtualField.find(HttpContent.class, JettyContentContext.class);
  private static final VirtualField<HttpExchange, JettyContentContext> EXCHANGE_CONTEXT =
      VirtualField.find(HttpExchange.class, JettyContentContext.class);
  private static final ThreadLocal<JettyContentContext> initializingContent = new ThreadLocal<>();
  private static final ThreadLocal<JettyContentContext> activeCallback = new ThreadLocal<>();

  private final Context context;

  JettyContentContext(Context caller) {
    context = caller;
  }

  @Nullable
  @Override
  public <V> V get(ContextKey<V> key) {
    return context.get(key);
  }

  @Override
  public <V> Context with(ContextKey<V> key, V value) {
    return context.with(key, value);
  }

  @Override
  public Scope makeCurrent() {
    // Response proxies use this context, so response callbacks share the reentrancy frame.
    CallbackScope scope = open();
    return scope == null ? Scope.noop() : scope;
  }

  @Nullable
  public CallbackScope open() {
    // Reentrant Jetty calls must not overwrite a context explicitly chosen by application code.
    if (activeCallback.get() == this) {
      return null;
    }
    JettyContentContext previous = activeCallback.get();
    Scope scope = context.makeCurrent();
    activeCallback.set(this);
    return new CallbackScope(scope, previous);
  }

  @Nullable
  public static JettyContentContext getCaller(HttpContent content) {
    return CONTENT_CONTEXT.get(content);
  }

  @Nullable
  public static JettyContentContext getCaller(HttpExchange exchange) {
    return EXCHANGE_CONTEXT.get(exchange);
  }

  public static void initialize(HttpExchange exchange, Request request) {
    EXCHANGE_CONTEXT.set(exchange, JettyRequestContext.getCaller(request));
  }

  public static void initialize(HttpContent content) {
    // In Jetty 9.2 a transport failure may call failed() after close(). The non-reused content
    // owns this immutable state for its lifetime, without a reference back to the carrier.
    CONTENT_CONTEXT.set(content, initializingContent.get());
  }

  @Nullable
  public static JettyContentContext initializing(@Nullable JettyContentContext context) {
    JettyContentContext previous = initializingContent.get();
    if (context == null) {
      initializingContent.remove();
    } else {
      initializingContent.set(context);
    }
    return previous;
  }

  public static final class CallbackScope implements Scope {
    private final Scope scope;
    @Nullable private final JettyContentContext previous;

    private CallbackScope(Scope scope, @Nullable JettyContentContext previous) {
      this.scope = scope;
      this.previous = previous;
    }

    @Override
    public void close() {
      if (previous == null) {
        activeCallback.remove();
      } else {
        activeCallback.set(previous);
      }
      scope.close();
    }
  }
}
