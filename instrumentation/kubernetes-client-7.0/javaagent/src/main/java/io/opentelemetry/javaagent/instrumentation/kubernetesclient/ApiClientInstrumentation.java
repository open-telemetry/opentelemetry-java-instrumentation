/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kubernetesclient;

import static io.opentelemetry.javaagent.instrumentation.kubernetesclient.KubernetesClientTracer.tracer;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.kubernetes.client.openapi.ApiCallback;
import io.kubernetes.client.openapi.ApiResponse;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
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

  public static class BuildRequestAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(@Advice.Return(readOnly = false) Request request) {
      Context parentContext = Java8BytecodeBridge.currentContext();
      if (!tracer().shouldStartSpan(parentContext)) {
        return;
      }

      Request.Builder requestWithPropagation = request.newBuilder();
      Context context = tracer().startSpan(parentContext, request, requestWithPropagation);
      CurrentContextAndScope.set(parentContext, context);
      request = requestWithPropagation.build();
    }
  }

  public static class ExecuteAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExit(
        @Advice.Return ApiResponse<?> response, @Advice.Thrown Throwable throwable) {
      Context context = CurrentContextAndScope.removeAndClose();
      if (context == null) {
        return;
      }
      if (throwable == null) {
        tracer().end(context, response);
      } else {
        tracer().endExceptionally(context, response, throwable);
      }
    }
  }

  public static class ExecuteAsyncAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) Call httpCall,
        @Advice.Argument(value = 2, readOnly = false) ApiCallback<?> callback,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {

      CurrentContextAndScope current = CurrentContextAndScope.remove();
      if (current != null) {
        context = current.getContext();
        scope = current.getScope();
        callback = new TracingApiCallback<>(callback, current.getParentContext(), context);
      }
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExit(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (scope == null) {
        return;
      }
      scope.close();

      if (throwable != null) {
        tracer().endExceptionally(context, throwable);
      }
      // else span will be ended in the TracingApiCallback
    }
  }
}
