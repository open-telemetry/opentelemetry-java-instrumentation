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

import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.function.BiConsumer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
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

  @SuppressWarnings("unused")
  public static class CreateAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static CallDepth onEnter() {
      CallDepth callDepth = CallDepth.forClass(HttpClient.class);
      callDepth.getAndIncrement();
      return callDepth;
    }

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static HttpClient stopSpan(
        @Advice.Thrown Throwable throwable,
        @Advice.Return HttpClient client,
        @Advice.Enter CallDepth callDepth) {

      if (callDepth.decrementAndGet() == 0 && throwable == null) {
        return client.doOnRequest(new OnRequest()).mapConnect(new MapConnect());
      }
      return client;
    }
  }

  @SuppressWarnings("unused")
  public static class OnRequestAdvice {

    @AssignReturned.ToArguments(@ToArgument(0))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static BiConsumer<? super HttpClientRequest, ? super Connection> onEnter(
        @Advice.Argument(0)
            BiConsumer<? super HttpClientRequest, ? super Connection> originalCallback) {
      BiConsumer<? super HttpClientRequest, ? super Connection> callback = originalCallback;
      if (DecoratorFunctions.shouldDecorate(callback.getClass())) {
        callback = new DecoratorFunctions.OnRequestDecorator(callback);
      }
      return callback;
    }
  }

  @SuppressWarnings("unused")
  public static class OnRequestErrorAdvice {

    @AssignReturned.ToArguments(@ToArgument(0))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static BiConsumer<? super HttpClientRequest, ? super Throwable> onEnter(
        @Advice.Argument(0) BiConsumer<? super HttpClientRequest, ? super Throwable> callback) {
      if (DecoratorFunctions.shouldDecorate(callback.getClass())) {
        return new DecoratorFunctions.OnRequestErrorDecorator(callback);
      }
      return callback;
    }
  }

  @SuppressWarnings("unused")
  public static class OnResponseAdvice {

    @AssignReturned.ToArguments(@ToArgument(0))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static BiConsumer<? super HttpClientResponse, ? super Connection> onEnter(
        @Advice.Argument(0) BiConsumer<? super HttpClientResponse, ? super Connection> callback,
        @Advice.Origin("#m") String methodName) {
      if (DecoratorFunctions.shouldDecorate(callback.getClass())) {
        boolean forceParentContext = methodName.equals("doAfterResponse");
        return new DecoratorFunctions.OnResponseDecorator(callback, forceParentContext);
      }
      return callback;
    }
  }

  @SuppressWarnings("unused")
  public static class OnResponseErrorAdvice {

    @AssignReturned.ToArguments(@ToArgument(0))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static BiConsumer<? super HttpClientResponse, ? super Throwable> onEnter(
        @Advice.Argument(0) BiConsumer<? super HttpClientResponse, ? super Throwable> callback) {
      if (DecoratorFunctions.shouldDecorate(callback.getClass())) {
        return new DecoratorFunctions.OnResponseErrorDecorator(callback);
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
            BiConsumer<? super HttpClientRequest, ? super Throwable> requestCallback,
        @Advice.Argument(1)
            BiConsumer<? super HttpClientResponse, ? super Throwable> responseCallback) {
      if (DecoratorFunctions.shouldDecorate(requestCallback.getClass())) {
        requestCallback = new DecoratorFunctions.OnRequestErrorDecorator(requestCallback);
      }
      if (DecoratorFunctions.shouldDecorate(responseCallback.getClass())) {
        responseCallback = new DecoratorFunctions.OnResponseErrorDecorator(responseCallback);
      }
      return new Object[] {requestCallback, responseCallback};
    }
  }
}
