/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kubernetesclient;

import static io.opentelemetry.javaagent.instrumentation.kubernetesclient.KubernetesClientTracer.tracer;
import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.kubernetes.client.openapi.ApiCallback;
import io.kubernetes.client.openapi.ApiResponse;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import okhttp3.Call;
import okhttp3.Request;

@AutoService(InstrumentationModule.class)
public class KubernetesClientInstrumentationModule extends InstrumentationModule {

  public KubernetesClientInstrumentationModule() {
    super("kubernetes-client", "kubernetes-client-7.0");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new ApiClientInstrumentation());
  }

  public static class ApiClientInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return named("io.kubernetes.client.openapi.ApiClient");
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      Map<ElementMatcher<MethodDescription>, String> transformers = new HashMap<>();
      transformers.put(
          isPublic().and(named("buildRequest")).and(takesArguments(10)),
          KubernetesClientInstrumentationModule.class.getName() + "$BuildRequestAdvice");
      transformers.put(
          isPublic()
              .and(named("execute"))
              .and(takesArguments(2))
              .and(takesArgument(0, named("okhttp3.Call"))),
          KubernetesClientInstrumentationModule.class.getName() + "$ExecuteAdvice");
      transformers.put(
          isPublic()
              .and(named("executeAsync"))
              .and(takesArguments(3))
              .and(takesArgument(0, named("okhttp3.Call")))
              .and(takesArgument(2, named("io.kubernetes.client.openapi.ApiCallback"))),
          KubernetesClientInstrumentationModule.class.getName() + "$ExecuteAsyncAdvice");
      return transformers;
    }
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
