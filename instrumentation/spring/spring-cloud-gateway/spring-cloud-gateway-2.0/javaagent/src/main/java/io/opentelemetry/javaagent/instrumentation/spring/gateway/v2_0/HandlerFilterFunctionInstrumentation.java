/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.gateway.v2_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class HandlerFilterFunctionInstrumentation implements TypeInstrumentation {

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
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(@Advice.Argument(0) Object request) {
      Context context = Java8BytecodeBridge.currentContext();
      //      HttpServerRoute.update(
      //          context,
      //          HttpServerRouteSource.NESTED_CONTROLLER,
      //          (ctx, req) -> ServerRequestHelper.extractServerRoute(req),
      //          request);
      //      ServerRequestHelper.extractAttributes(request, context);
    }
  }
}
