/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.javahttpclient;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.instrumentation.javahttpclient.JavaHttpClientSingletons.instrumenter;
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
import io.opentelemetry.instrumentation.javahttpclient.internal.ResponseInfoCapture;
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

class HttpClientInstrumentation implements TypeInstrumentation {

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
        named("send")
            .and(isPublic())
            .and(takesArguments(2))
            .and(takesArgument(0, named("java.net.http.HttpRequest"))),
        getClass().getName() + "$SendAdvice");

    transformer.applyAdviceToMethod(
        named("sendAsync")
            .and(isPublic())
            .and(takesArgument(0, named("java.net.http.HttpRequest"))),
        getClass().getName() + "$SendAsyncAdvice");
  }

  @SuppressWarnings("unused")
  public static class SendAdvice {

    public static class AdviceScope {
      private final Context context;
      private final Scope scope;
      private final HttpRequest request;
      private final ResponseInfoCapture responseInfoCapture;

      private AdviceScope(
          Context context,
          Scope scope,
          HttpRequest request,
          ResponseInfoCapture responseInfoCapture) {
        this.context = context;
        this.scope = scope;
        this.request = request;
        this.responseInfoCapture = responseInfoCapture;
      }

      @Nullable
      public static AdviceScope start(HttpRequest request) {
        Context parentContext = Context.current();
        if (!instrumenter().shouldStart(parentContext, request)) {
          return null;
        }

        Context context = instrumenter().start(parentContext, request);
        return new AdviceScope(context, context.makeCurrent(), request, new ResponseInfoCapture());
      }

      public <T> HttpResponse.BodyHandler<T> wrap(HttpResponse.BodyHandler<T> bodyHandler) {
        return responseInfoCapture.wrap(bodyHandler);
      }

      public void end(@Nullable HttpResponse<?> response, @Nullable Throwable throwable) {
        scope.close();
        if (response == null && throwable != null) {
          response = responseInfoCapture.response(request, throwable);
        }
        instrumenter().end(context, request, response, throwable);
      }
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static AdviceScope methodEnter(
        @Advice.Argument(value = 0) HttpRequest httpRequest,
        @Advice.Argument(value = 1, readOnly = false)
            HttpResponse.BodyHandler<?> responseBodyHandler) {
      AdviceScope adviceScope = AdviceScope.start(httpRequest);
      if (adviceScope != null) {
        responseBodyHandler = adviceScope.wrap(responseBodyHandler);
      }
      return adviceScope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void methodExit(
        @Advice.Return @Nullable HttpResponse<?> httpResponse,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AdviceScope scope) {

      if (scope != null) {
        scope.end(httpResponse, throwable);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class SendAsyncAdvice {

    public static class AsyncAdviceScope {
      @Nullable private final Context parentContext;
      @Nullable private final Context context;
      @Nullable private final Scope scope;
      private final CallDepth callDepth;
      private final HttpRequest request;
      @Nullable private final ResponseInfoCapture responseInfoCapture;

      private AsyncAdviceScope(
          @Nullable Context parentContext,
          @Nullable Context context,
          @Nullable Scope scope,
          CallDepth callDepth,
          HttpRequest request,
          @Nullable ResponseInfoCapture responseInfoCapture) {
        this.parentContext = parentContext;
        this.context = context;
        this.scope = scope;
        this.callDepth = callDepth;
        this.request = request;
        this.responseInfoCapture = responseInfoCapture;
      }

      public <T> HttpResponse.BodyHandler<T> wrap(HttpResponse.BodyHandler<T> bodyHandler) {
        if (responseInfoCapture == null) {
          return bodyHandler;
        }
        return responseInfoCapture.wrap(bodyHandler);
      }

      @Nullable
      public static AsyncAdviceScope start(HttpRequest request) {
        CallDepth callDepth = CallDepth.forClass(HttpClient.class);
        if (callDepth.getAndIncrement() > 0) {
          return new AsyncAdviceScope(null, null, null, callDepth, request, null);
        }
        Context parentContext = Context.current();
        if (!instrumenter().shouldStart(parentContext, request)) {
          callDepth.decrementAndGet();
          return null;
        }
        Context context = instrumenter().start(parentContext, request);
        ResponseInfoCapture responseInfoCapture = new ResponseInfoCapture();
        return new AsyncAdviceScope(
            parentContext, context, context.makeCurrent(), callDepth, request, responseInfoCapture);
      }

      public CompletableFuture<HttpResponse<?>> end(
          @Nullable Throwable throwable, CompletableFuture<HttpResponse<?>> future) {
        if (callDepth.decrementAndGet() > 0 || scope == null) {
          // async end nested call
          return future;
        }
        scope.close();
        if (throwable != null) {
          // async end with exception: ending span and no wrapping needed
          instrumenter().end(context, request, null, throwable);
          return future;
        }
        return CompletableFutureWrapper.wrap(future, parentContext)
            .whenComplete(
                new ResponseConsumer(instrumenter(), context, request, responseInfoCapture));
      }
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static AsyncAdviceScope methodEnter(
        @Advice.Argument(value = 0) HttpRequest httpRequest,
        @Advice.Argument(value = 1, readOnly = false)
            HttpResponse.BodyHandler<?> responseBodyHandler) {
      AsyncAdviceScope adviceScope = AsyncAdviceScope.start(httpRequest);
      if (adviceScope != null) {
        responseBodyHandler = adviceScope.wrap(responseBodyHandler);
      }
      return adviceScope;
    }

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static CompletableFuture<HttpResponse<?>> methodExit(
        @Advice.Return CompletableFuture<HttpResponse<?>> future,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AsyncAdviceScope scope) {
      return scope == null ? future : scope.end(throwable, future);
    }
  }
}
