/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.server.route;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import akka.http.scaladsl.model.Uri;
import akka.http.scaladsl.server.PathMatcher;
import akka.http.scaladsl.server.PathMatchers;
import io.opentelemetry.instrumentation.api.util.VirtualField;
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
      if (result.getClass() == PathMatcher.Matched.class) {
        if (PathMatchers.PathEnd$.class == pathMatcher.getClass()) {
          AkkaRouteHolder.endMatched();
          return;
        }
        // if present use the matched path that was remembered in PathMatcherInstrumentation,
        // otherwise just use a *
        String prefix = VirtualField.find(PathMatcher.class, String.class).get(pathMatcher);
        if (prefix == null) {
          if (PathMatchers.Slash$.class == pathMatcher.getClass()) {
            prefix = "/";
          } else {
            prefix = "*";
          }
        }
        if (prefix != null) {
          AkkaRouteHolder.push(prefix);
        }
      }
    }
  }
}
