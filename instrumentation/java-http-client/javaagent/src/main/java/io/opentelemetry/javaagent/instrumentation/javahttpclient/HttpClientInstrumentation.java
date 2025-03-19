/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.javahttpclient;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.instrumentation.javahttpclient.JavaHttpClientSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.javahttpclient.internal.CompletableFutureWrapper;
import io.opentelemetry.instrumentation.javahttpclient.internal.ResponseConsumer;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class HttpClientInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("java.net.http.HttpClient");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return nameStartsWith("java.net.")
        .or(nameStartsWith("jdk.internal."))
        .and(not(named("jdk.internal.net.http.HttpClientFacade")))
        .and(extendsClass(named("java.net.http.HttpClient")));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("send"))
            .and(isPublic())
            .and(takesArguments(2))
            .and(takesArgument(0, named("java.net.http.HttpRequest"))),
        HttpClientInstrumentation.class.getName() + "$SendAdvice");

    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("sendAsync"))
            .and(isPublic())
            .and(takesArgument(0, named("java.net.http.HttpRequest"))),
        HttpClientInstrumentation.class.getName() + "$SendAsyncAdvice");
  }

  @SuppressWarnings("unused")
  public static class SendAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.Argument(value = 0) HttpRequest httpRequest,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      Context parentContext = currentContext();
      if (!instrumenter().shouldStart(parentContext, httpRequest)) {
        return;
      }

      context = instrumenter().start(parentContext, httpRequest);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Argument(0) HttpRequest httpRequest,
        @Advice.Return HttpResponse<?> httpResponse,
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (scope == null) {
        return;
      }

      scope.close();
      instrumenter().end(context, httpRequest, httpResponse, throwable);
    }
  }

  @SuppressWarnings("unused")
  public static class SendAsyncAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.Argument(value = 0) HttpRequest httpRequest,
        @Advice.Local("otelCallDepth") CallDepth callDepth,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelParentContext") Context parentContext,
        @Advice.Local("otelScope") Scope scope) {
      callDepth = CallDepth.forClass(HttpClient.class);
      if (callDepth.getAndIncrement() > 0) {
        return;
      }

      parentContext = currentContext();
      if (!instrumenter().shouldStart(parentContext, httpRequest)) {
        return;
      }

      context = instrumenter().start(parentContext, httpRequest);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Argument(value = 0) HttpRequest httpRequest,
        @Advice.Return(readOnly = false) CompletableFuture<HttpResponse<?>> future,
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelCallDepth") CallDepth callDepth,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelParentContext") Context parentContext,
        @Advice.Local("otelScope") Scope scope) {
      if (callDepth.decrementAndGet() > 0) {
        return;
      }

      if (scope == null) {
        return;
      }

      scope.close();
      if (throwable != null) {
        instrumenter().end(context, httpRequest, null, throwable);
      } else {
        future = future.whenComplete(new ResponseConsumer(instrumenter(), context, httpRequest));
        future = CompletableFutureWrapper.wrap(future, parentContext);
      }
    }
  }
}
