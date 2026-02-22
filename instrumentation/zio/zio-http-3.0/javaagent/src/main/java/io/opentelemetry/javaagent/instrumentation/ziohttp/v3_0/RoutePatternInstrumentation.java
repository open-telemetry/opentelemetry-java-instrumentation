/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ziohttp.v3_0;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import zio.http.Handler;
import zio.http.RoutePattern;

public class RoutePatternInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("zio.http.RoutePattern");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(named("$minus$greater")).and(takesArgument(0, named("zio.http.Handler"))),
        getClass().getName() + "$CreateRouteAdvice");
  }

  @SuppressWarnings("unused")
  public static final class CreateRouteAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    @Advice.AssignReturned.ToArguments(@ToArgument(0))
    public static Handler<?, ?, ?, ?> onEnter(
        @Advice.This RoutePattern<?> routePattern,
        @Advice.Argument(0) Handler<?, ?, ?, ?> handler) {
      return HandlerWrapper.wrap(handler, routePattern);
    }
  }
}
