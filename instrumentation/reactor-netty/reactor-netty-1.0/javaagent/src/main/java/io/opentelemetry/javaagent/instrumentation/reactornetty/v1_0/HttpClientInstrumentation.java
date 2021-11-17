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
            .and(namedOneOf("doOnRequest", "doAfterRequest"))
            .and(takesArguments(1))
            .and(takesArgument(0, BiConsumer.class)),
        this.getClass().getName() + "$OnRequestAdvice");
    transformer.applyAdviceToMethod(
        isPublic()
            .and(named("doOnRequestError"))
            .and(takesArguments(1))
            .and(takesArgument(0, BiConsumer.class)),
        this.getClass().getName() + "$OnRequestErrorAdvice");
    transformer.applyAdviceToMethod(
        isPublic()
            .and(namedOneOf("doOnResponse", "doAfterResponseSuccess", "doOnRedirect"))
            .and(takesArguments(1))
            .and(takesArgument(0, BiConsumer.class)),
        this.getClass().getName() + "$OnResponseAdvice");
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

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(value = 0, readOnly = false)
            BiConsumer<? super HttpClientRequest, ? super Connection> callback,
        @Advice.Origin("#m") String methodName) {

      if (DecoratorFunctions.shouldDecorate(callback.getClass())) {
        // use client context after request is sent, parent context before that
        PropagatedContext propagatedContext =
            "doAfterRequest".equals(methodName)
                ? PropagatedContext.CLIENT
                : PropagatedContext.PARENT;
        callback = new DecoratorFunctions.OnMessageDecorator<>(callback, propagatedContext);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class OnRequestErrorAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(value = 0, readOnly = false)
            BiConsumer<? super HttpClientRequest, ? super Throwable> callback) {

      if (DecoratorFunctions.shouldDecorate(callback.getClass())) {
        callback =
            new DecoratorFunctions.OnMessageErrorDecorator<>(callback, PropagatedContext.PARENT);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class OnResponseAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(value = 0, readOnly = false)
            BiConsumer<? super HttpClientResponse, ? super Connection> callback,
        @Advice.Origin("#m") String methodName) {

      if (DecoratorFunctions.shouldDecorate(callback.getClass())) {
        // use client context just when response status & headers are received, the parent context
        // after the response is completed
        PropagatedContext propagatedContext =
            "doOnResponse".equals(methodName) ? PropagatedContext.CLIENT : PropagatedContext.PARENT;
        callback = new DecoratorFunctions.OnMessageDecorator<>(callback, propagatedContext);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class OnResponseErrorAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(value = 0, readOnly = false)
            BiConsumer<? super HttpClientResponse, ? super Throwable> callback) {

      if (DecoratorFunctions.shouldDecorate(callback.getClass())) {
        callback =
            new DecoratorFunctions.OnMessageErrorDecorator<>(callback, PropagatedContext.PARENT);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class OnErrorAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(value = 0, readOnly = false)
            BiConsumer<? super HttpClientRequest, ? super Throwable> requestCallback,
        @Advice.Argument(value = 1, readOnly = false)
            BiConsumer<? super HttpClientResponse, ? super Throwable> responseCallback) {

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
    }
  }
}
