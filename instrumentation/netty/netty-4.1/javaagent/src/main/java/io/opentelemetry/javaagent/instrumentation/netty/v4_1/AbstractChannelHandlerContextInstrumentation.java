/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_1;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.Attribute;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.netty.v4_1.AttributeKeys;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.netty.common.server.NettyServerErrorHandler;
import io.opentelemetry.javaagent.instrumentation.netty.v4_1.client.NettyHttpClientTracer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class AbstractChannelHandlerContextInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.netty.channel.AbstractChannelHandlerContext");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("invokeExceptionCaught"))
            .and(takesArgument(0, named(Throwable.class.getName()))),
        AbstractChannelHandlerContextInstrumentation.class.getName()
            + "$InvokeExceptionCaughtAdvice");
  }

  @SuppressWarnings("unused")
  public static class InvokeExceptionCaughtAdvice {

    @Advice.OnMethodEnter
    public static void onEnter(
        @Advice.This ChannelHandlerContext channelContext,
        @Advice.Argument(0) Throwable throwable) {
      if (channelContext.channel().hasAttr(AttributeKeys.CLIENT_CONTEXT)) {
        Attribute<Context> clientContextAttr =
            channelContext.channel().attr(AttributeKeys.CLIENT_CONTEXT);
        NettyHttpClientTracer.tracer().endExceptionally(clientContextAttr.get(), throwable);
      } else if (channelContext.channel().hasAttr(AttributeKeys.SERVER_CONTEXT)) {
        Attribute<Context> serverContextAttr =
            channelContext.channel().attr(AttributeKeys.SERVER_CONTEXT);
        NettyServerErrorHandler.onError(serverContextAttr.get(), throwable);
      }
    }
  }
}
