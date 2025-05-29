/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.server.route;

import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class RouteConcatenationInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        // scala 2.11
        "akka.http.scaladsl.server.RouteConcatenation$RouteWithConcatenation$$anonfun$$tilde$1",
        // scala 2.12 and later
        "akka.http.scaladsl.server.RouteConcatenation$RouteWithConcatenation");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        namedOneOf(
            // scala 2.11
            "apply",
            // scala 2.12 and later
            "$anonfun$$tilde$1"),
        this.getClass().getName() + "$ApplyAdvice");

    // This advice seems to be only needed when defining routes with java dsl. Since java dsl tests
    // use scala 2.12 we are going to skip instrumenting this for scala 2.11.
    transformer.applyAdviceToMethod(
        namedOneOf("$anonfun$$tilde$2"), this.getClass().getName() + "$Apply2Advice");
  }

  @SuppressWarnings("unused")
  public static class ApplyAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter() {
      // when routing dsl uses concat(path(...) {...}, path(...) {...}) we'll restore the currently
      // matched route after each matcher so that match attempts that failed wouldn't get recorded
      // in the route
      AkkaRouteHolder.save();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit() {
      AkkaRouteHolder.restore();
    }
  }

  @SuppressWarnings("unused")
  public static class Apply2Advice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter() {
      AkkaRouteHolder.reset();
    }
  }
}
