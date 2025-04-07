/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.server.route;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import akka.http.scaladsl.model.Uri;
import akka.http.scaladsl.server.PathMatcher;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class PathMatcherInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("akka.http.scaladsl.server.PathMatcher$");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("apply")
            .and(takesArgument(0, named("akka.http.scaladsl.model.Uri$Path")))
            .and(returns(named("akka.http.scaladsl.server.PathMatcher"))),
        this.getClass().getName() + "$ApplyAdvice");
  }

  @SuppressWarnings("unused")
  public static class ApplyAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) Uri.Path prefix, @Advice.Return PathMatcher<?> result) {
      // store the path being matched inside a VirtualField on the given matcher, so it can be used
      // for constructing the route
      PathMatcherUtil.setMatched(result, prefix.toString());
    }
  }
}
