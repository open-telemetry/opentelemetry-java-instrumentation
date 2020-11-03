/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.sparkjava;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import spark.routematch.RouteMatch;

@AutoService(Instrumenter.class)
public class RoutesInstrumentation extends Instrumenter.Default {

  public RoutesInstrumentation() {
    super("sparkjava", "sparkjava-2.4");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("spark.route.Routes");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      RoutesInstrumentation.class.getName() + "$TracerHolder",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        named("find")
            .and(takesArgument(0, named("spark.route.HttpMethod")))
            .and(returns(named("spark.routematch.RouteMatch")))
            .and(isPublic()),
        RoutesInstrumentation.class.getName() + "$RoutesAdvice");
  }

  public static class TracerHolder {
    private static final Tracer TRACER =
        OpenTelemetry.getGlobalTracer("io.opentelemetry.auto.sparkjava-2.3");

    public static Tracer tracer() {
      return TRACER;
    }
  }

  public static class RoutesAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void routeMatchEnricher(@Advice.Return RouteMatch routeMatch) {

      Span span = Java8BytecodeBridge.currentSpan();
      if (span != null && routeMatch != null) {
        span.updateName(routeMatch.getMatchUri());
      }
    }
  }
}
