/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server.route;

import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class PathConcatenationInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.pekko.http.scaladsl.server.PathMatcher");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("$anonfun$append$1"), this.getClass().getName() + "$ApplyAdvice");
  }

  @SuppressWarnings("unused")
  public static class ApplyAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter() {
      // https://github.com/apache/incubator-pekko-http/blob/bea7d2b5c21e23d55556409226d136c282da27a3/http/src/main/scala/org/apache/pekko/http/scaladsl/server/PathMatcher.scala#L53
      // https://github.com/apache/incubator-pekko-http/blob/bea7d2b5c21e23d55556409226d136c282da27a3/http/src/main/scala/org/apache/pekko/http/scaladsl/server/PathMatcher.scala#L57
      // when routing dsl uses path("path1" / "path2") we are concatenating 3 segments "path1" and /
      // and "path2" we need to notify the matcher that a new segment has started, so it could be
      // captured in the route
      PekkoRouteHolder.startSegment();
    }
  }
}
