/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.sparkjava;

import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import java.util.List;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import spark.routematch.RouteMatch;

@AutoService(InstrumentationModule.class)
public class SparkInstrumentationModule extends InstrumentationModule {

  public SparkInstrumentationModule() {
    super("spark", "spark-2.3");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new RoutesInstrumentation());
  }

  public static class RoutesInstrumentation implements TypeInstrumentation {
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
          SparkInstrumentationModule.class.getName() + "$RoutesAdvice");
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
