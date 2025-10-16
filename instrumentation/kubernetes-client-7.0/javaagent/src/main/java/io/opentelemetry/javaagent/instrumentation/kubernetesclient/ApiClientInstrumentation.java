/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kubernetesclient;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
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
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
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
        isPublic().and(named("buildRequest")).and(takesArguments(10).or(takesArguments(11))),
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

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static Request onExit(@Advice.Return Request originalReturnValue) {
      Context parentContext = currentContext();
      if (!instrumenter().shouldStart(parentContext, originalReturnValue)) {
        return originalReturnValue;
      }

      Context context = instrumenter().start(parentContext, originalReturnValue);
      Scope scope = context.makeCurrent();
      Request.Builder requestWithPropagation = originalReturnValue.newBuilder();
      inject(context, requestWithPropagation);
      Request returnValue = requestWithPropagation.build();
      CurrentState.set(parentContext, context, scope, originalReturnValue);
      return returnValue;
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

    public static class AdviceScope {
      private final Scope scope;
      private final CurrentState current;

      private AdviceScope(CurrentState current, Scope scope) {
        this.current = current;
        this.scope = scope;
      }

      public static AdviceScope start(CurrentState current) {
        return new AdviceScope(current, current.getContext().makeCurrent());
      }

      public ApiCallback<?> wrapCallBack(ApiCallback<?> callback) {
        return new TracingApiCallback<>(
            callback, current.getParentContext(), current.getContext(), current.getRequest());
      }

      public void end(@Nullable Throwable throwable) {
        scope.close();
        if (throwable != null) {
          instrumenter().end(current.getContext(), current.getRequest(), null, throwable);
        }
        // else span will be ended in the TracingApiCallback
      }
    }

    @AssignReturned.ToArguments(@ToArgument(value = 2, index = 1))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Object[] onEnter(
        @Advice.Argument(0) Call httpCall, @Advice.Argument(2) ApiCallback<?> callback) {
      CurrentState current = CurrentState.remove();
      if (current == null) {
        return new Object[] {null, callback};
      }
      AdviceScope adviceScope = AdviceScope.start(current);
      return new Object[] {adviceScope, adviceScope.wrapCallBack(callback)};
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable throwable, @Advice.Enter Object[] enterResult) {
      AdviceScope adviceScope = (AdviceScope) enterResult[0];
      if (adviceScope != null) {
        adviceScope.end(throwable);
      }
    }
  }
}
