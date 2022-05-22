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
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpRouteHolder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpRouteSource;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.lang.reflect.Method;
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

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void nameSpan(
        @Advice.This Object target,
        @Advice.Origin Method method,
        @Advice.Local("otelCallDepth") CallDepth callDepth,
        @Advice.Local("otelHandlerData") HandlerData handlerData,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      callDepth = CallDepth.forClass(Path.class);
      if (callDepth.getAndIncrement() > 0) {
        return;
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
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelCallDepth") CallDepth callDepth,
        @Advice.Local("otelHandlerData") HandlerData handlerData,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (callDepth.decrementAndGet() > 0) {
        return;
      }

      if (scope == null) {
        return;
      }
      scope.close();
      instrumenter().end(context, handlerData, null, throwable);
    }
  }
}
