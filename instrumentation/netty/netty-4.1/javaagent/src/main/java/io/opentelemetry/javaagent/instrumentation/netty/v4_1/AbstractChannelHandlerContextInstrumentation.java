/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_1;

import static io.opentelemetry.javaagent.instrumentation.netty.v4_1.NettyClientSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpContentDecoder;
import io.netty.handler.codec.http.HttpObject;
import io.opentelemetry.instrumentation.netty.common.internal.NettyErrorHolder;
import io.opentelemetry.instrumentation.netty.v4_1.internal.ServerContext;
import io.opentelemetry.instrumentation.netty.v4_1.internal.ServerContexts;
import io.opentelemetry.instrumentation.netty.v4_1.internal.client.ClientContexts;
import io.opentelemetry.instrumentation.netty.v4_1.internal.client.ClientContexts.ResponseCallback;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class AbstractChannelHandlerContextInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.netty.channel.AbstractChannelHandlerContext");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("invokeExceptionCaught").and(takesArgument(0, named(Throwable.class.getName()))),
        getClass().getName() + "$InvokeExceptionCaughtAdvice");
    transformer.applyAdviceToMethod(
        named("invokeChannelRead").and(takesArguments(1)).and(takesArgument(0, Object.class)),
        getClass().getName() + "$InvokeChannelReadAdvice");
  }

  @SuppressWarnings("unused")
  public static class InvokeExceptionCaughtAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter(
        @Advice.This ChannelHandlerContext ctx, @Advice.Argument(0) Throwable throwable) {

      // we can't rely on exception handling in HttpClientTracingHandler because it can't catch
      // exceptions from handlers that run after it, for example ratpack has ReadTimeoutHandler
      // (trigger ReadTimeoutException) after HttpClientCodec (or handler is inserted after it)
      ClientContexts.fail(ctx.channel(), instrumenter(), throwable);
      ServerContext serverContext = ServerContexts.peekFirst(ctx.channel());
      if (serverContext != null) {
        NettyErrorHolder.set(serverContext.context(), throwable);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class InvokeChannelReadAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static int onEnter(
        @Advice.This ChannelHandlerContext ctx, @Advice.Argument(0) Object msg) {
      ResponseCallback callback = null;
      boolean fromField = false;
      if (msg instanceof HttpObject && ClientContexts.hasResponseCallback(ctx.channel())) {
        callback = ClientContexts.currentResponseCallback(ctx.channel(), msg);
        if (callback == null) {
          callback = NettyClientSingletons.RESPONSE_CALLBACK.get((HttpObject) msg);
          fromField = callback != null;
        }
        if (callback == null) {
          callback = ClientContexts.decodedResponseCallback(ctx.channel(), msg);
        }
      }
      int previousDepth =
          ClientContexts.enterChannelRead(
              ctx.channel(),
              msg,
              callback,
              ctx,
              callback != null && ctx.handler() instanceof HttpContentDecoder);
      return fromField && previousDepth >= 0 ? -previousDepth - 2 : previousDepth;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Argument(0) Object msg, @Advice.Enter int previousDepth) {
      if (previousDepth < -1) {
        NettyClientSingletons.RESPONSE_CALLBACK.set((HttpObject) msg, null);
        previousDepth = -previousDepth - 2;
      }
      ClientContexts.exitChannelRead(previousDepth);
    }
  }
}
