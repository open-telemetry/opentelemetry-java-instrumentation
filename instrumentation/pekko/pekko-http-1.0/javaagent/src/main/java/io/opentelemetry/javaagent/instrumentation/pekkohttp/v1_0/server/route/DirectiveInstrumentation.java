/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server.route;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.pekko.http.scaladsl.server.RequestContext;
import org.apache.pekko.http.scaladsl.server.RouteResult;
import scala.Function1;
import scala.concurrent.Future;

public class DirectiveInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return extendsClass(named("org.apache.pekko.http.scaladsl.server.Directive"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("tapply").and(takesArguments(1)), this.getClass().getName() + "$ApplyAdvice");
  }

  @SuppressWarnings("unused")
  public static class ApplyAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    @Advice.AssignReturned.ToReturned
    public static Function1<RequestContext, Future<RouteResult>> onExit(
        @Advice.Return Function1<RequestContext, Future<RouteResult>> route) {

      return new PekkoRouteWrapper(route);
    }
  }
}
