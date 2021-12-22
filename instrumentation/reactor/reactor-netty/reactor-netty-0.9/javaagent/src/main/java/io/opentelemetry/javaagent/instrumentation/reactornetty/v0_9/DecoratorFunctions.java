/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v0_9;

import io.netty.channel.Channel;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.netty.v4_1.AttributeKeys;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import reactor.netty.Connection;
import reactor.netty.http.client.HttpClientRequest;
import reactor.netty.http.client.HttpClientResponse;

public final class DecoratorFunctions {

  // ignore our own callbacks - or already decorated functions
  public static boolean shouldDecorate(Class<?> callbackClass) {
    return !callbackClass.getName().startsWith("io.opentelemetry.javaagent");
  }

  private abstract static class OnMessageDecorator<M> implements BiConsumer<M, Connection> {
    private final BiConsumer<? super M, ? super Connection> delegate;
    private final boolean forceParentContext;

    protected OnMessageDecorator(
        BiConsumer<? super M, ? super Connection> delegate, boolean forceParentContext) {
      this.delegate = delegate;
      this.forceParentContext = forceParentContext;
    }

    @Override
    public final void accept(M message, Connection connection) {
      Channel channel = connection.channel();
      // don't try to get the client span from the netty channel when forceParentSpan is true
      // this way the parent context will always be propagated
      if (forceParentContext) {
        channel = null;
      }
      Context context = getChannelContext(currentContext(message), channel);
      if (context == null) {
        delegate.accept(message, connection);
      } else {
        try (Scope ignored = context.makeCurrent()) {
          delegate.accept(message, connection);
        }
      }
    }

    abstract reactor.util.context.Context currentContext(M message);
  }

  public static final class OnRequestDecorator extends OnMessageDecorator<HttpClientRequest> {
    public OnRequestDecorator(BiConsumer<? super HttpClientRequest, ? super Connection> delegate) {
      super(delegate, /* forceParentContext= */ false);
    }

    @Override
    reactor.util.context.Context currentContext(HttpClientRequest message) {
      return message.currentContext();
    }
  }

  public static final class OnResponseDecorator extends OnMessageDecorator<HttpClientResponse> {
    public OnResponseDecorator(
        BiConsumer<? super HttpClientResponse, ? super Connection> delegate,
        boolean forceParentContext) {
      super(delegate, forceParentContext);
    }

    @Override
    reactor.util.context.Context currentContext(HttpClientResponse message) {
      return message.currentContext();
    }
  }

  private abstract static class OnMessageErrorDecorator<M> implements BiConsumer<M, Throwable> {
    private final BiConsumer<? super M, ? super Throwable> delegate;

    protected OnMessageErrorDecorator(BiConsumer<? super M, ? super Throwable> delegate) {
      this.delegate = delegate;
    }

    @Override
    public final void accept(M message, Throwable throwable) {
      Context context = getChannelContext(currentContext(message), null);
      if (context == null) {
        delegate.accept(message, throwable);
      } else {
        try (Scope ignored = context.makeCurrent()) {
          delegate.accept(message, throwable);
        }
      }
    }

    abstract reactor.util.context.Context currentContext(M message);
  }

  public static final class OnRequestErrorDecorator
      extends OnMessageErrorDecorator<HttpClientRequest> {
    public OnRequestErrorDecorator(
        BiConsumer<? super HttpClientRequest, ? super Throwable> delegate) {
      super(delegate);
    }

    @Override
    reactor.util.context.Context currentContext(HttpClientRequest message) {
      return message.currentContext();
    }
  }

  public static final class OnResponseErrorDecorator
      extends OnMessageErrorDecorator<HttpClientResponse> {
    public OnResponseErrorDecorator(
        BiConsumer<? super HttpClientResponse, ? super Throwable> delegate) {
      super(delegate);
    }

    @Override
    reactor.util.context.Context currentContext(HttpClientResponse message) {
      return message.currentContext();
    }
  }

  @Nullable
  private static Context getChannelContext(
      reactor.util.context.Context reactorContext, @Nullable Channel channel) {
    // try to get the client span context from the channel if it's available
    if (channel != null) {
      Context context = channel.attr(AttributeKeys.CLIENT_CONTEXT).get();
      if (context != null) {
        return context;
      }
    }
    // otherwise use the parent span context
    return reactorContext.getOrDefault(MapConnect.CONTEXT_ATTRIBUTE, null);
  }

  private DecoratorFunctions() {}
}
