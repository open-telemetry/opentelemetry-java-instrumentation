/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kubernetesclient;

import static io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.kubernetesclient.KubernetesClientSingletons.inject;
import static io.opentelemetry.javaagent.instrumentation.kubernetesclient.KubernetesClientSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.kubernetes.client.openapi.ApiCallback;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.ApiResponse;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import okhttp3.Call;
import okhttp3.Request;

public class ApiClientInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.kubernetes.client.openapi.ApiClient");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isPublic().and(named("buildRequest")).and(takesArguments(10)),
        this.getClass().getName() + "$BuildRequestAdvice");
    transformer.applyAdviceToMethod(
        isPublic()
            .and(named("execute"))
            .and(takesArguments(2))
            .and(takesArgument(0, named("okhttp3.Call"))),
        this.getClass().getName() + "$ExecuteAdvice");
    transformer.applyAdviceToMethod(
        isPublic()
            .and(named("executeAsync"))
            .and(takesArguments(3))
            .and(takesArgument(0, named("okhttp3.Call")))
            .and(takesArgument(2, named("io.kubernetes.client.openapi.ApiCallback"))),
        this.getClass().getName() + "$ExecuteAsyncAdvice");
  }

  @SuppressWarnings("unused")
  public static class BuildRequestAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(@Advice.Return(readOnly = false) Request request) {
      Context parentContext = currentContext();
      if (!instrumenter().shouldStart(parentContext, request)) {
        return;
      }

      Context context = instrumenter().start(parentContext, request);
      Scope scope = context.makeCurrent();
      Request.Builder requestWithPropagation = request.newBuilder();
      inject(context, requestWithPropagation);
      request = requestWithPropagation.build();
      CurrentState.set(parentContext, context, scope, request);
    }
  }

  @SuppressWarnings("unused")
  public static class ExecuteAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExit(
        @Advice.Return ApiResponse<?> response, @Advice.Thrown Throwable throwable) {
      CurrentState currentState = CurrentState.remove();
      if (currentState == null) {
        return;
      }

      currentState.getScope().close();
      Context context = currentState.getContext();
      ApiResponse<?> endResponse = response;
      if (response == null && throwable instanceof ApiException) {
        ApiException apiException = (ApiException) throwable;
        endResponse = new ApiResponse<>(apiException.getCode(), apiException.getResponseHeaders());
      }
      instrumenter().end(context, currentState.getRequest(), endResponse, throwable);
    }
  }

  @SuppressWarnings("unused")
  public static class ExecuteAsyncAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) Call httpCall,
        @Advice.Argument(value = 2, readOnly = false) ApiCallback<?> callback,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Local("otelRequest") Request request) {
      CurrentState current = CurrentState.remove();
      if (current == null) {
        return;
      }

      context = current.getContext();
      scope = current.getScope();
      request = current.getRequest();
      callback = new TracingApiCallback<>(callback, current.getParentContext(), context, request);
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExit(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Local("otelRequest") Request request) {
      if (scope == null) {
        return;
      }
      scope.close();

      if (throwable != null) {
        instrumenter().end(context, request, null, throwable);
      }
      // else span will be ended in the TracingApiCallback
    }
  }
}
