/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.gateway.webmvc.v5_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.web.servlet.function.ServerRequest;

public class GatewayDelegatingRouterFunctionInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed(
        "org.springframework.cloud.gateway.server.mvc.handler.GatewayDelegatingRouterFunction");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named(
        "org.springframework.cloud.gateway.server.mvc.handler.GatewayDelegatingRouterFunction");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(named("route"))
            .and(takesArgument(0, named("org.springframework.web.servlet.function.ServerRequest")))
            .and(takesArguments(1)),
        this.getClass().getName() + "$RouteAdvice");
  }

  @SuppressWarnings("unused")
  public static class RouteAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void methodExit(
        @Advice.This Object thisObj, @Advice.Argument(0) ServerRequest request) {
      Context context = Context.current();
      // Record gateway route info as attributes
      // The HTTP route should remain the actual path pattern from Spring WebMVC
      ServerRequestHelper.extractAttributes(thisObj, request, context);
    }
  }
}
