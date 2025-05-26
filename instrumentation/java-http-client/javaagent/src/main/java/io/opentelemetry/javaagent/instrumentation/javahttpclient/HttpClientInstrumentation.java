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
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
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

  public static class AdviceScope {
    private final Context context;
    private final Context parentContext;
    private final Scope scope;
    private final CallDepth callDepth;
    private final HttpRequest request;

    private AdviceScope(
        Context parentContext,
        Context context,
        Scope scope,
        CallDepth callDepth,
        HttpRequest request) {
      this.parentContext = parentContext;
      this.context = context;
      this.scope = scope;
      this.callDepth = callDepth;
      this.request = request;
    }

    @Nullable
    public static AdviceScope start(HttpRequest request) {
      return start(request, null);
    }

    private static AdviceScope start(HttpRequest request, CallDepth callDepth) {
      Context parentContext = currentContext();
      if (!instrumenter().shouldStart(parentContext, request)) {
        return null;
      }

      Context context = instrumenter().start(parentContext, request);
      return new AdviceScope(parentContext, context, context.makeCurrent(), callDepth, request);
    }

    public void end(HttpRequest request, HttpResponse<?> response, Throwable throwable) {
      scope.close();
      instrumenter().end(context, request, response, throwable);
    }

    public static AdviceScope startWithCallDepth(HttpRequest httpRequest) {
      CallDepth callDepth = CallDepth.forClass(HttpClient.class);
      if (callDepth.getAndIncrement() > 0) {
        return new AdviceScope(null, null, null, callDepth, null);
      }
      return start(httpRequest, callDepth);
    }

    public boolean endWithCallDepth(HttpRequest httpRequest, Throwable throwable) {

      if (callDepth.decrementAndGet() > 0) {
        return false;
      }

      scope.close();
      if (throwable != null) {
        instrumenter().end(context, httpRequest, null, throwable);
        return false;
      }
      return true;
    }

    public CompletableFuture<HttpResponse<?>> wrapFuture(
        CompletableFuture<HttpResponse<?>> future) {
      future = future.whenComplete(new ResponseConsumer(instrumenter(), context, request));
      return CompletableFutureWrapper.wrap(future, parentContext);
    }
  }

  @SuppressWarnings("unused")
  public static class SendAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope methodEnter(@Advice.Argument(value = 0) HttpRequest httpRequest) {
      return AdviceScope.start(httpRequest);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Argument(0) HttpRequest httpRequest,
        @Advice.Return HttpResponse<?> httpResponse,
        @Advice.Thrown Throwable throwable,
        @Advice.Enter @Nullable AdviceScope scope) {

      if (scope != null) {
        scope.end(httpRequest, httpResponse, throwable);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class SendAsyncAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope methodEnter(@Advice.Argument(value = 0) HttpRequest httpRequest) {
      return AdviceScope.startWithCallDepth(httpRequest);
    }

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static CompletableFuture<HttpResponse<?>> methodExit(
        @Advice.Argument(value = 0) HttpRequest httpRequest,
        @Advice.Return CompletableFuture<HttpResponse<?>> future,
        @Advice.Thrown Throwable throwable,
        @Advice.Enter AdviceScope scope) {

      if (!scope.endWithCallDepth(httpRequest, throwable)) {
        return future;
      }

      return scope.wrapFuture(future);
    }
  }
}
