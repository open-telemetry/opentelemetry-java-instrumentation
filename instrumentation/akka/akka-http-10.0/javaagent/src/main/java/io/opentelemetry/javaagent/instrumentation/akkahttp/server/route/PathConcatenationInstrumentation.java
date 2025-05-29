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

public class PathConcatenationInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "akka.http.scaladsl.server.PathMatcher$$anonfun$$tilde$1",
        "akka.http.scaladsl.server.PathMatcher");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        namedOneOf("apply", "$anonfun$append$1"), this.getClass().getName() + "$ApplyAdvice");
  }

  @SuppressWarnings("unused")
  public static class ApplyAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter() {
      // https://github.com/akka/akka-http/blob/0fedb87671ecc450e7378713105ea1dc1d9d0c7d/akka-http/src/main/scala/akka/http/scaladsl/server/PathMatcher.scala#L43
      // https://github.com/akka/akka-http/blob/0fedb87671ecc450e7378713105ea1dc1d9d0c7d/akka-http/src/main/scala/akka/http/scaladsl/server/PathMatcher.scala#L47
      // when routing dsl uses path("path1" / "path2") we are concatenating 3 segments "path1" and /
      // and "path2" we need to notify the matcher that a new segment has started, so it could be
      // captured in the route
      AkkaRouteHolder.startSegment();
    }
  }
}
