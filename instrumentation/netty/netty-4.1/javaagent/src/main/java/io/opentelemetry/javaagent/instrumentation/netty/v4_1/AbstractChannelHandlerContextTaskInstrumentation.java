/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_1;

import static io.opentelemetry.javaagent.instrumentation.netty.v4_1.NettyClientSingletons.FAILURE_NOTIFICATION_TASK;
import static io.opentelemetry.javaagent.instrumentation.netty.v4_1.NettyClientSingletons.RESPONSE_CALLBACK;
import static io.opentelemetry.javaagent.instrumentation.netty.v4_1.NettyClientSingletons.RESPONSE_CALLBACK_TASK;
import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpObject;
import io.opentelemetry.instrumentation.netty.v4_1.internal.client.ClientContexts;
import io.opentelemetry.instrumentation.netty.v4_1.internal.client.ClientContexts.DispatchScope;
import io.opentelemetry.instrumentation.netty.v4_1.internal.client.ClientContexts.FailureNotification;
import io.opentelemetry.instrumentation.netty.v4_1.internal.client.ClientContexts.ResponseCallback;
import io.opentelemetry.instrumentation.netty.v4_1.internal.client.ClientContexts.ResponseCallbackDispatch;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class AbstractChannelHandlerContextTaskInstrumentation implements TypeInstrumentation {

  private static final String CONTEXT_PREFIX = "io.netty.channel.AbstractChannelHandlerContext$";

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return nameStartsWith(CONTEXT_PREFIX)
        .and(hasSuperType(named(Runnable.class.getName())))
        .and(
            declaresMethod(
                isConstructor()
                    .and(takesArguments(2))
                    .and(takesArgument(0, named("io.netty.channel.AbstractChannelHandlerContext")))
                    .and(takesArgument(1, Object.class).or(takesArgument(1, Throwable.class)))));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    ElementMatcher.Junction<MethodDescription> constructorMatcher =
        isConstructor()
            .and(takesArguments(2))
            .and(takesArgument(0, named("io.netty.channel.AbstractChannelHandlerContext")))
            .and(takesArgument(1, Object.class).or(takesArgument(1, Throwable.class)));
    transformer.applyAdviceToMethod(
        constructorMatcher, getClass().getName() + "$CaptureDispatchAdvice");
    transformer.applyAdviceToMethod(
        named("run").and(takesArguments(0)), getClass().getName() + "$RunDispatchAdvice");
  }

  @SuppressWarnings("unused")
  public static class CaptureDispatchAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This Runnable task,
        @Advice.Argument(0) ChannelHandlerContext ctx,
        @Advice.Argument(1) Object event) {
      if (event instanceof HttpObject) {
        ResponseCallback callback = ClientContexts.currentResponseCallback(ctx.channel(), event);
        boolean fromField = false;
        if (callback == null) {
          callback = RESPONSE_CALLBACK.get((HttpObject) event);
          fromField = callback != null;
        }
        if (callback != null) {
          ResponseCallbackDispatch dispatch =
              ClientContexts.responseCallbackForMessage(callback, event);
          RESPONSE_CALLBACK_TASK.set(task, dispatch);
          if (fromField) {
            RESPONSE_CALLBACK.set((HttpObject) event, null);
          }
        }
      } else if (event instanceof Throwable) {
        FailureNotification notification =
            ClientContexts.currentFailureNotification(ctx.channel(), (Throwable) event);
        if (notification != null) {
          FAILURE_NOTIFICATION_TASK.set(task, notification);
        }
      }
    }
  }

  @SuppressWarnings("unused")
  public static class RunDispatchAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static DispatchScope onEnter(@Advice.This Runnable task) {
      ResponseCallbackDispatch callback = RESPONSE_CALLBACK_TASK.get(task);
      FailureNotification notification = FAILURE_NOTIFICATION_TASK.get(task);
      if (callback != null) {
        RESPONSE_CALLBACK_TASK.set(task, null);
      }
      if (notification != null) {
        FAILURE_NOTIFICATION_TASK.set(task, null);
      }
      return ClientContexts.enterDispatch(callback, notification);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable DispatchScope scope) {
      ClientContexts.exitDispatch(scope);
    }
  }
}
