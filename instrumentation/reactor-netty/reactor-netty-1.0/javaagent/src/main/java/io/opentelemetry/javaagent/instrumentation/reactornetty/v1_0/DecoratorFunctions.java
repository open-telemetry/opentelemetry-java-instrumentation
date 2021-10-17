/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

import io.netty.channel.Channel;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.netty.v4_1.AttributeKeys;
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

    public OnMessageDecorator(BiConsumer<? super M, ? super Connection> delegate) {
      this.delegate = delegate;
    }

    @Override
    public void accept(M message, Connection connection) {
      Context context = getChannelContext(message.currentContextView(), connection.channel());
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

    public OnMessageErrorDecorator(BiConsumer<? super M, ? super Throwable> delegate) {
      this.delegate = delegate;
    }

    @Override
    public void accept(M message, Throwable throwable) {
      Context context = getChannelContext(message.currentContextView(), null);
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
  private static Context getChannelContext(ContextView contextView, @Nullable Channel channel) {
    // try to get the client span context from the channel if it's available
    if (channel != null) {
      Context context = channel.attr(AttributeKeys.CLIENT_CONTEXT).get();
      if (context != null) {
        return context;
      }
    }
    // otherwise use the parent span context
    return contextView.getOrDefault(MapConnect.CONTEXT_ATTRIBUTE, null);
  }

  private DecoratorFunctions() {}
}
