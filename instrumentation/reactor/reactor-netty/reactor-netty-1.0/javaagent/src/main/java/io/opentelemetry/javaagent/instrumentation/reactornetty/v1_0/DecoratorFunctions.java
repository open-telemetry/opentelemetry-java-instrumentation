/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import reactor.netty.Connection;
import reactor.netty.http.client.HttpClientInfos;
import reactor.util.context.ContextView;

public final class DecoratorFunctions {

  // ignore our own callbacks - or already decorated functions
  public static boolean shouldDecorate(Class<?> callbackClass) {
    return !callbackClass.getName().startsWith("io.opentelemetry.javaagent");
  }

  public static final class OnMessageDecorator<M extends HttpClientInfos>
      implements BiConsumer<M, Connection> {

    private final BiConsumer<? super M, ? super Connection> delegate;
    private final PropagatedContext propagatedContext;

    public OnMessageDecorator(
        BiConsumer<? super M, ? super Connection> delegate, PropagatedContext propagatedContext) {
      this.delegate = delegate;
      this.propagatedContext = propagatedContext;
    }

    @Override
    public void accept(M message, Connection connection) {
      Context context = getChannelContext(message.currentContextView(), propagatedContext);
      if (context == null) {
        delegate.accept(message, connection);
      } else {
        try (Scope ignored = context.makeCurrent()) {
          delegate.accept(message, connection);
        }
      }
    }
  }

  public static final class OnMessageErrorDecorator<M extends HttpClientInfos>
      implements BiConsumer<M, Throwable> {

    private final BiConsumer<? super M, ? super Throwable> delegate;
    private final PropagatedContext propagatedContext;

    public OnMessageErrorDecorator(
        BiConsumer<? super M, ? super Throwable> delegate, PropagatedContext propagatedContext) {
      this.delegate = delegate;
      this.propagatedContext = propagatedContext;
    }

    @Override
    public void accept(M message, Throwable throwable) {
      Context context = getChannelContext(message.currentContextView(), propagatedContext);
      if (context == null) {
        delegate.accept(message, throwable);
      } else {
        try (Scope ignored = context.makeCurrent()) {
          delegate.accept(message, throwable);
        }
      }
    }
  }

  @Nullable
  private static Context getChannelContext(
      ContextView contextView, PropagatedContext propagatedContext) {
    Context context = null;
    if (propagatedContext.useClientContext) {
      context = contextView.getOrDefault(ReactorContextKeys.CLIENT_CONTEXT_KEY, null);
    }
    if (context == null) {
      context = contextView.getOrDefault(ReactorContextKeys.CLIENT_PARENT_CONTEXT_KEY, null);
    }
    return context;
  }

  public enum PropagatedContext {
    PARENT(false),
    CLIENT(true);

    final boolean useClientContext;

    PropagatedContext(boolean useClientContext) {
      this.useClientContext = useClientContext;
    }
  }

  private DecoratorFunctions() {}
}
