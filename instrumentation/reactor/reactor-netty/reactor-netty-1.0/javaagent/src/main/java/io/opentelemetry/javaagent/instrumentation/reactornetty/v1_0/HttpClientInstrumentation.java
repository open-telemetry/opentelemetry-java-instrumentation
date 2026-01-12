/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0.DecoratorFunctions.PropagatedContext;
import java.util.function.BiConsumer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import reactor.netty.Connection;
import reactor.netty.http.client.HttpClientRequest;
import reactor.netty.http.client.HttpClientResponse;

public class HttpClientInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("reactor.netty.http.client.HttpClient");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // advice classes below expose current context in doOn*/doAfter* callbacks
    transformer.applyAdviceToMethod(
        isPublic()
            .and(named("doOnRequest"))
            .and(takesArguments(1))
            .and(takesArgument(0, BiConsumer.class)),
        this.getClass().getName() + "$OnRequestAdvice");
    transformer.applyAdviceToMethod(
        isPublic()
            .and(named("doAfterRequest"))
            .and(takesArguments(1))
            .and(takesArgument(0, BiConsumer.class)),
        this.getClass().getName() + "$AfterRequestAdvice");
    transformer.applyAdviceToMethod(
        isPublic()
            .and(named("doOnRequestError"))
            .and(takesArguments(1))
            .and(takesArgument(0, BiConsumer.class)),
        this.getClass().getName() + "$OnRequestErrorAdvice");
    transformer.applyAdviceToMethod(
        isPublic()
            .and(named("doOnResponse"))
            .and(takesArguments(1))
            .and(takesArgument(0, BiConsumer.class)),
        this.getClass().getName() + "$OnResponseAdvice");
    transformer.applyAdviceToMethod(
        isPublic()
            .and(namedOneOf("doAfterResponseSuccess", "doOnRedirect"))
            .and(takesArguments(1))
            .and(takesArgument(0, BiConsumer.class)),
        this.getClass().getName() + "$AfterResponseAdvice");
    transformer.applyAdviceToMethod(
        isPublic()
            .and(named("doOnResponseError"))
            .and(takesArguments(1))
            .and(takesArgument(0, BiConsumer.class)),
        this.getClass().getName() + "$OnResponseErrorAdvice");
    transformer.applyAdviceToMethod(
        isPublic()
            .and(named("doOnError"))
            .and(takesArguments(2))
            .and(takesArgument(0, BiConsumer.class))
            .and(takesArgument(1, BiConsumer.class)),
        this.getClass().getName() + "$OnErrorAdvice");
  }

  @SuppressWarnings("unused")
  public static class OnRequestAdvice {

    @AssignReturned.ToArguments(@ToArgument(0))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static BiConsumer<? super HttpClientRequest, ? super Connection> onEnter(
        @Advice.Argument(0)
            BiConsumer<? super HttpClientRequest, ? super Connection> originalCallBack) {

      // intermediate variable needed for inlined instrumentation
      BiConsumer<? super HttpClientRequest, ? super Connection> callback = originalCallBack;

      if (DecoratorFunctions.shouldDecorate(callback.getClass())) {
        // perform the callback with the client span active (instead of the parent) since this
        // callback occurs after the connection is made
        callback = new DecoratorFunctions.OnMessageDecorator<>(callback, PropagatedContext.CLIENT);
      }
      return callback;
    }
  }

  @SuppressWarnings("unused")
  public static class AfterRequestAdvice {

    @AssignReturned.ToArguments(@ToArgument(0))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static BiConsumer<? super HttpClientRequest, ? super Connection> onEnter(
        @Advice.Argument(0)
            BiConsumer<? super HttpClientRequest, ? super Connection> originalCallBack) {

      // intermediate variable needed for inlined instrumentation
      BiConsumer<? super HttpClientRequest, ? super Connection> callback = originalCallBack;

      if (DecoratorFunctions.shouldDecorate(callback.getClass())) {
        // use client context after request is sent
        callback = new DecoratorFunctions.OnMessageDecorator<>(callback, PropagatedContext.CLIENT);
      }
      return callback;
    }
  }

  @SuppressWarnings("unused")
  public static class OnRequestErrorAdvice {

    @AssignReturned.ToArguments(@ToArgument(0))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static BiConsumer<? super HttpClientRequest, ? super Throwable> onEnter(
        @Advice.Argument(0)
            BiConsumer<? super HttpClientRequest, ? super Throwable> originalCallBack) {

      // using an intermediate variable is required to keep the advice when inlined
      BiConsumer<? super HttpClientRequest, ? super Throwable> callback = originalCallBack;

      if (DecoratorFunctions.shouldDecorate(callback.getClass())) {
        callback =
            new DecoratorFunctions.OnMessageErrorDecorator<>(callback, PropagatedContext.PARENT);
      }
      return callback;
    }
  }

  @SuppressWarnings("unused")
  public static class OnResponseAdvice {

    @AssignReturned.ToArguments(@ToArgument(0))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static BiConsumer<? super HttpClientResponse, ? super Connection> onEnter(
        @Advice.Argument(0)
            BiConsumer<? super HttpClientResponse, ? super Connection> originalCallBack) {

      // intermediate variable needed for inlined instrumentation
      BiConsumer<? super HttpClientResponse, ? super Connection> callback = originalCallBack;

      if (DecoratorFunctions.shouldDecorate(callback.getClass())) {
        // use client context just when response status & headers are received
        callback = new DecoratorFunctions.OnMessageDecorator<>(callback, PropagatedContext.CLIENT);
      }
      return callback;
    }
  }

  @SuppressWarnings("unused")
  public static class AfterResponseAdvice {

    @AssignReturned.ToArguments(@ToArgument(0))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static BiConsumer<? super HttpClientResponse, ? super Connection> onEnter(
        @Advice.Argument(0)
            BiConsumer<? super HttpClientResponse, ? super Connection> originalCallback) {

      // intermediate variable needed for inlined instrumentation
      BiConsumer<? super HttpClientResponse, ? super Connection> callback = originalCallback;

      if (DecoratorFunctions.shouldDecorate(callback.getClass())) {
        callback = new DecoratorFunctions.OnMessageDecorator<>(callback, PropagatedContext.PARENT);
      }
      return callback;
    }
  }

  @SuppressWarnings("unused")
  public static class OnResponseErrorAdvice {

    @AssignReturned.ToArguments(@ToArgument(0))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static BiConsumer<? super HttpClientResponse, ? super Throwable> onEnter(
        @Advice.Argument(0)
            BiConsumer<? super HttpClientResponse, ? super Throwable> originalCallback) {

      // intermediate variable needed for inlined instrumentation
      BiConsumer<? super HttpClientResponse, ? super Throwable> callback = originalCallback;

      if (DecoratorFunctions.shouldDecorate(callback.getClass())) {
        callback =
            new DecoratorFunctions.OnMessageErrorDecorator<>(callback, PropagatedContext.PARENT);
      }
      return callback;
    }
  }

  @SuppressWarnings("unused")
  public static class OnErrorAdvice {

    @AssignReturned.ToArguments({
      @ToArgument(value = 0, index = 0),
      @ToArgument(value = 1, index = 1)
    })
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Object[] onEnter(
        @Advice.Argument(0)
            BiConsumer<? super HttpClientRequest, ? super Throwable> originalRequestCallback,
        @Advice.Argument(1)
            BiConsumer<? super HttpClientResponse, ? super Throwable> originalResponseCallback) {

      // intermediate variables needed for inlined instrumentation
      BiConsumer<? super HttpClientRequest, ? super Throwable> requestCallback =
          originalRequestCallback;
      BiConsumer<? super HttpClientResponse, ? super Throwable> responseCallback =
          originalResponseCallback;

      if (DecoratorFunctions.shouldDecorate(requestCallback.getClass())) {
        requestCallback =
            new DecoratorFunctions.OnMessageErrorDecorator<>(
                requestCallback, PropagatedContext.PARENT);
      }
      if (DecoratorFunctions.shouldDecorate(responseCallback.getClass())) {
        responseCallback =
            new DecoratorFunctions.OnMessageErrorDecorator<>(
                responseCallback, PropagatedContext.PARENT);
      }
      return new Object[] {requestCallback, responseCallback};
    }
  }
}
