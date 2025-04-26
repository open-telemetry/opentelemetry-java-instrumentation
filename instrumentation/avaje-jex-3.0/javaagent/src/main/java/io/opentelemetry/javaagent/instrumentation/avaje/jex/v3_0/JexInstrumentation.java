/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.avaje.jex.v3_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.avaje.jex.http.Context;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRoute;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteSource;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class JexInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("io.avaje.jex.http.ExchangeHandler");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return hasSuperType(named("io.avaje.jex.http.ExchangeHandler")).and(not(isInterface()));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("handle").and(takesArgument(0, named("io.avaje.jex.http.Context"))),
        this.getClass().getName() + "$HandlerAdapterAdvice");
  }

  @SuppressWarnings("unused")
  public static class HandlerAdapterAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onMethodExecute(@Advice.Argument(0) Context ctx) {
      HttpServerRoute.update(
          io.opentelemetry.context.Context.current(),
          HttpServerRouteSource.CONTROLLER,
          ctx.matchedPath());
    }
  }
}
