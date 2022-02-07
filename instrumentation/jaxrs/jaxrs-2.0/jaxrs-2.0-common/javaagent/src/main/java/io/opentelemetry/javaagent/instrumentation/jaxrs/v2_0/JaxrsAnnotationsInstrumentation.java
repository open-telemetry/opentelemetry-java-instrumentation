/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasSuperMethod;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasSuperType;
import static io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0.JaxrsSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpRouteHolder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpRouteSource;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.CallDepth;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import java.lang.reflect.Method;
import java.util.concurrent.CompletionStage;
import javax.ws.rs.Path;
import javax.ws.rs.container.AsyncResponse;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner.Typing;
import net.bytebuddy.matcher.ElementMatcher;

public class JaxrsAnnotationsInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("javax.ws.rs.Path");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return hasSuperType(
        isAnnotatedWith(named("javax.ws.rs.Path"))
            .or(declaresMethod(isAnnotatedWith(named("javax.ws.rs.Path")))));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(
                hasSuperMethod(
                    isAnnotatedWith(
                        namedOneOf(
                            "javax.ws.rs.Path",
                            "javax.ws.rs.DELETE",
                            "javax.ws.rs.GET",
                            "javax.ws.rs.HEAD",
                            "javax.ws.rs.OPTIONS",
                            "javax.ws.rs.PATCH",
                            "javax.ws.rs.POST",
                            "javax.ws.rs.PUT")))),
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
        @Advice.Local("otelHandlerData") HandlerData handlerData,
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
      handlerData = new HandlerData(target.getClass(), method);

      HttpRouteHolder.updateHttpRoute(
          parentContext,
          HttpRouteSource.CONTROLLER,
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
        @Advice.Local("otelHandlerData") HandlerData handlerData,
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

      if (asyncResponse != null && !asyncResponse.isSuspended()) {
        // Clear span from the asyncResponse. Logically this should never happen. Added to be safe.
        VirtualField.find(AsyncResponse.class, AsyncResponseData.class).set(asyncResponse, null);
      }
      if (asyncReturnValue != null) {
        // span finished by CompletionStageFinishCallback
        asyncReturnValue =
            asyncReturnValue.handle(new CompletionStageFinishCallback<>(context, handlerData));
      }
      if ((asyncResponse == null || !asyncResponse.isSuspended()) && asyncReturnValue == null) {
        instrumenter().end(context, handlerData, null, null);
      }
      // else span finished by AsyncResponse*Advice
    }
  }
}
