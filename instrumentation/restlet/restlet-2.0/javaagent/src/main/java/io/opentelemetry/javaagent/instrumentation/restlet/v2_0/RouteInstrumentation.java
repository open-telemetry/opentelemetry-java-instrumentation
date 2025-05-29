/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.restlet.v2_0;

import static io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteSource.CONTROLLER;
import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.restlet.v2_0.RestletSingletons.serverSpanName;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRoute;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.restlet.Request;
import org.restlet.routing.TemplateRoute;

public class RouteInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf("org.restlet.routing.TemplateRoute", "org.restlet.routing.Route");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("beforeHandle"))
            .and(takesArgument(0, named("org.restlet.Request")))
            .and(takesArgument(1, named("org.restlet.Response"))),
        this.getClass().getName() + "$RouteBeforeHandleAdvice");
  }

  @SuppressWarnings("unused")
  public static class RouteBeforeHandleAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void getRouteInfo(
        @Advice.This TemplateRoute route, @Advice.Argument(0) Request request) {
      String pattern = route.getTemplate().getPattern();

      HttpServerRoute.update(currentContext(), CONTROLLER, serverSpanName(), pattern);
    }
  }
}
