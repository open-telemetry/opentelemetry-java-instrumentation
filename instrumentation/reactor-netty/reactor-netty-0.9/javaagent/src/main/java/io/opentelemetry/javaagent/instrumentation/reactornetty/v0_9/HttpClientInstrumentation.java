/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v0_9;

import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.CallDepthThreadLocalMap;
import java.util.function.BiConsumer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import reactor.netty.Connection;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientRequest;
import reactor.netty.http.client.HttpClientResponse;

public class HttpClientInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("reactor.netty.http.client.HttpClient");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isStatic().and(namedOneOf("create", "newConnection", "from")),
        this.getClass().getName() + "$CreateAdvice");

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
            .and(namedOneOf("doOnResponse", "doAfterResponse"))
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

  public static class CreateAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter() {
      CallDepthThreadLocalMap.incrementCallDepth(HttpClient.class);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown Throwable throwable, @Advice.Return(readOnly = false) HttpClient client) {

      if (CallDepthThreadLocalMap.decrementCallDepth(HttpClient.class) == 0 && throwable == null) {
        client = client.doOnRequest(new OnRequest()).mapConnect(new MapConnect());
      }
    }
  }

  public static class OnRequestAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(value = 0, readOnly = false)
            BiConsumer<? super HttpClientRequest, ? super Connection> callback) {
      if (DecoratorFunctions.shouldDecorate(callback.getClass())) {
        callback = new DecoratorFunctions.OnRequestDecorator(callback);
      }
    }
  }

  public static class OnRequestErrorAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(value = 0, readOnly = false)
            BiConsumer<? super HttpClientRequest, ? super Throwable> callback) {
      if (DecoratorFunctions.shouldDecorate(callback.getClass())) {
        callback = new DecoratorFunctions.OnRequestErrorDecorator(callback);
      }
    }
  }

  public static class OnResponseAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(value = 0, readOnly = false)
            BiConsumer<? super HttpClientResponse, ? super Connection> callback,
        @Advice.Origin("#m") String methodName) {
      if (DecoratorFunctions.shouldDecorate(callback.getClass())) {
        boolean forceParentContext = methodName.equals("doAfterResponse");
        callback = new DecoratorFunctions.OnResponseDecorator(callback, forceParentContext);
      }
    }
  }

  public static class OnResponseErrorAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(value = 0, readOnly = false)
            BiConsumer<? super HttpClientResponse, ? super Throwable> callback) {
      if (DecoratorFunctions.shouldDecorate(callback.getClass())) {
        callback = new DecoratorFunctions.OnResponseErrorDecorator(callback);
      }
    }
  }

  public static class OnErrorAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(value = 0, readOnly = false)
            BiConsumer<? super HttpClientRequest, ? super Throwable> requestCallback,
        @Advice.Argument(value = 1, readOnly = false)
            BiConsumer<? super HttpClientResponse, ? super Throwable> responseCallback) {
      if (DecoratorFunctions.shouldDecorate(requestCallback.getClass())) {
        requestCallback = new DecoratorFunctions.OnRequestErrorDecorator(requestCallback);
      }
      if (DecoratorFunctions.shouldDecorate(responseCallback.getClass())) {
        responseCallback = new DecoratorFunctions.OnResponseErrorDecorator(responseCallback);
      }
    }
  }
}
