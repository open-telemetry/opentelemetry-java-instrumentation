/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v3_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasSuperMethod;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasSuperType;
import static io.opentelemetry.javaagent.instrumentation.jaxrs.v3_0.JaxrsAnnotationsSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.not;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRoute;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteSource;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.jaxrs.AsyncResponseData;
import io.opentelemetry.javaagent.instrumentation.jaxrs.CompletionStageFinishCallback;
import io.opentelemetry.javaagent.instrumentation.jaxrs.JaxrsServerSpanNaming;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.container.AsyncResponse;
import java.lang.reflect.Method;
import java.util.concurrent.CompletionStage;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner.Typing;
import net.bytebuddy.matcher.ElementMatcher;

public class JaxrsAnnotationsInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return hasSuperType(
        isAnnotatedWith(named("jakarta.ws.rs.Path"))
            .or(declaresMethod(isAnnotatedWith(named("jakarta.ws.rs.Path")))));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(not(isStatic()))
            .and(
                hasSuperMethod(
                    isAnnotatedWith(
                        namedOneOf(
                            "jakarta.ws.rs.Path",
                            "jakarta.ws.rs.DELETE",
                            "jakarta.ws.rs.GET",
                            "jakarta.ws.rs.HEAD",
                            "jakarta.ws.rs.OPTIONS",
                            "jakarta.ws.rs.PATCH",
                            "jakarta.ws.rs.POST",
                            "jakarta.ws.rs.PUT")))),
        JaxrsAnnotationsInstrumentation.class.getName() + "$JaxRsAnnotationsAdvice");
  }

  @SuppressWarnings("unused")
  public static class JaxRsAnnotationsAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void nameSpan(
        @Advice.This Object target,
        @Advice.Origin Method method,
        @Advice.AllArguments Object[] args,
        @Advice.Local("otelCallDepth") CallDepth callDepth,
        @Advice.Local("otelHandlerData") Jaxrs3HandlerData handlerData,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Local("otelAsyncResponse") AsyncResponse asyncResponse) {
      callDepth = CallDepth.forClass(Path.class);
      if (callDepth.getAndIncrement() > 0) {
        return;
      }

      VirtualField<AsyncResponse, AsyncResponseData> virtualField = null;
      for (Object arg : args) {
        if (arg instanceof AsyncResponse) {
          asyncResponse = (AsyncResponse) arg;
          virtualField = VirtualField.find(AsyncResponse.class, AsyncResponseData.class);
          if (virtualField.get(asyncResponse) != null) {
            /*
             * We are probably in a recursive call and don't want to start a new span because it
             * would replace the existing span in the asyncResponse and cause it to never finish. We
             * could work around this by using a list instead, but we likely don't want the extra
             * span anyway.
             */
            return;
          }
          break;
        }
      }

      Context parentContext = Java8BytecodeBridge.currentContext();
      handlerData = new Jaxrs3HandlerData(target.getClass(), method);

      HttpServerRoute.update(
          parentContext,
          HttpServerRouteSource.CONTROLLER,
          JaxrsServerSpanNaming.SERVER_SPAN_NAME,
          handlerData);

      if (!instrumenter().shouldStart(parentContext, handlerData)) {
        return;
      }

      context = instrumenter().start(parentContext, handlerData);
      scope = context.makeCurrent();

      if (virtualField != null && asyncResponse != null) {
        virtualField.set(asyncResponse, AsyncResponseData.create(context, handlerData));
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Return(readOnly = false, typing = Typing.DYNAMIC) Object returnValue,
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelCallDepth") CallDepth callDepth,
        @Advice.Local("otelHandlerData") Jaxrs3HandlerData handlerData,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Local("otelAsyncResponse") AsyncResponse asyncResponse) {
      if (callDepth.decrementAndGet() > 0) {
        return;
      }

      if (scope == null) {
        return;
      }

      scope.close();

      if (throwable != null) {
        instrumenter().end(context, handlerData, null, throwable);
        return;
      }

      CompletionStage<?> asyncReturnValue =
          returnValue instanceof CompletionStage ? (CompletionStage<?>) returnValue : null;
      if (asyncReturnValue != null) {
        // span finished by CompletionStageFinishCallback
        asyncReturnValue =
            asyncReturnValue.handle(
                new CompletionStageFinishCallback<>(instrumenter(), context, handlerData));
      }
      if (asyncResponse == null && asyncReturnValue == null) {
        instrumenter().end(context, handlerData, null, null);
      }
      // else span finished by AsyncResponse*Advice
    }
  }
}
