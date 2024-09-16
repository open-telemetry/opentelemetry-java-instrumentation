/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesNoArguments;

import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.vertx.ext.web.RoutingContext;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class RoutingContextInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("io.vertx.ext.web.RoutingContext");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("io.vertx.ext.web.RoutingContext"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isPublic().and(named("next")).and(takesNoArguments()),
        this.getClass().getName() + "$NextAdvice");
  }

  @SuppressWarnings("unused")
  public static class NextAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void next(@Advice.This RoutingContext routingContext) {
      // calling next tells router to move to the next matching route
      // restore remembered route to remove currently matched route from it
      String previousRoute = RoutingContextUtil.getRoute(routingContext);
      if (previousRoute != null) {
        RouteHolder.set(Java8BytecodeBridge.currentContext(), previousRoute);
      }
    }
  }
}
