/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.sparkjava;

import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import spark.routematch.RouteMatch;

public class RoutesInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("spark.route.Routes");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("find")
            .and(takesArgument(0, named("spark.route.HttpMethod")))
            .and(returns(named("spark.routematch.RouteMatch")))
            .and(isPublic()),
        this.getClass().getName() + "$FindAdvice");
  }

  @SuppressWarnings("unused")
  public static class FindAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void routeMatchEnricher(@Advice.Return RouteMatch routeMatch) {
      SparkRouteUpdater.updateHttpRoute(routeMatch);
    }
  }
}
