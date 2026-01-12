/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.server.route;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.instrumentation.akkahttp.server.route.AkkaRouteUtil.PREFIX;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import akka.http.scaladsl.model.Uri;
import akka.http.scaladsl.server.PathMatcher;
import akka.http.scaladsl.server.PathMatchers;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class PathMatcherStaticInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return extendsClass(named("akka.http.scaladsl.server.PathMatcher"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("apply").and(takesArgument(0, named("akka.http.scaladsl.model.Uri$Path"))),
        this.getClass().getName() + "$ApplyAdvice");
  }

  @SuppressWarnings("unused")
  public static class ApplyAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.This PathMatcher<?> pathMatcher,
        @Advice.Argument(0) Uri.Path path,
        @Advice.Return PathMatcher.Matching<?> result) {
      // result is either matched or unmatched, we only care about the matches
      Context context = Java8BytecodeBridge.currentContext();
      AkkaRouteHolder routeHolder = AkkaRouteHolder.get(context);
      if (routeHolder == null) {
        return;
      }
      if (result.getClass() == PathMatcher.Matched.class) {
        PathMatcher.Matched<?> match = (PathMatcher.Matched<?>) result;
        // if present use the matched path that was remembered in PathMatcherInstrumentation,
        // otherwise just use a *
        String prefix = PREFIX.get(pathMatcher);
        if (prefix == null) {
          if (PathMatchers.Slash$.class == pathMatcher.getClass()) {
            prefix = "/";
          } else {
            prefix = "*";
          }
        }
        routeHolder.push(path, match.pathRest(), prefix);
      } else {
        routeHolder.didNotMatch();
      }
    }
  }
}
