/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v1_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasSuperMethod;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasSuperType;
import static io.opentelemetry.javaagent.instrumentation.jaxrs.v1_0.JaxrsSingletons.instrumenter;
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
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.lang.reflect.Method;
import javax.annotation.Nullable;
import javax.ws.rs.Path;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
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
            .and(not(isStatic()))
            .and(
                hasSuperMethod(
                    isAnnotatedWith(
                        namedOneOf(
                            "javax.ws.rs.Path",
                            "javax.ws.rs.DELETE",
                            "javax.ws.rs.GET",
                            "javax.ws.rs.HEAD",
                            "javax.ws.rs.OPTIONS",
                            "javax.ws.rs.POST",
                            "javax.ws.rs.PUT")))),
        JaxrsAnnotationsInstrumentation.class.getName() + "$JaxRsAnnotationsAdvice");
  }

  @SuppressWarnings("unused")
  public static class JaxRsAnnotationsAdvice {

    public static class AdviceScope {
      private final HandlerData handlerData;
      private final CallDepth callDepth;
      private final Context context;
      private final Scope scope;

      public AdviceScope(CallDepth callDepth, Class<?> type, Method method) {
        this.callDepth = callDepth;
        if (callDepth.getAndIncrement() > 0) {
          handlerData = null;
          context = null;
          scope = null;
          return;
        }

        Context parentContext = Context.current();
        handlerData = new HandlerData(type, method);

        HttpServerRoute.update(
            parentContext,
            HttpServerRouteSource.CONTROLLER,
            JaxrsServerSpanNaming.SERVER_SPAN_NAME,
            handlerData);

        if (!instrumenter().shouldStart(parentContext, handlerData)) {
          scope = null;
          context = null;
          return;
        }

        context = instrumenter().start(parentContext, handlerData);
        scope = context.makeCurrent();
      }

      public void exit(@Nullable Throwable throwable) {
        if (callDepth.decrementAndGet() > 0 || scope == null) {
          return;
        }
        scope.close();
        instrumenter().end(context, handlerData, null, throwable);
      }
    }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope nameSpan(@Advice.This Object target, @Advice.Origin Method method) {
      return new AdviceScope(CallDepth.forClass(Path.class), target.getClass(), method);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown @Nullable Throwable throwable, @Advice.Enter AdviceScope adviceScope) {
      adviceScope.exit(throwable);
    }
  }
}
