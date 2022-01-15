/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finatra;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.instrumentation.finatra.FinatraSingletons.instrumenter;
import static io.opentelemetry.javaagent.instrumentation.finatra.FinatraSingletons.updateServerSpanName;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.twitter.finagle.http.Response;
import com.twitter.finatra.http.contexts.RouteInfo;
import com.twitter.util.Future;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import scala.Some;

public class FinatraRouteInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("com.twitter.finatra.http.internal.routing.Route");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return nameStartsWith("com.twitter.finatra.")
        .and(extendsClass(named("com.twitter.finatra.http.internal.routing.Route")));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("handleMatch"))
            .and(takesArguments(2))
            .and(takesArgument(0, named("com.twitter.finagle.http.Request"))),
        this.getClass().getName() + "$HandleMatchAdvice");
  }

  @SuppressWarnings("unused")
  public static class HandleMatchAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void nameSpan(
        @Advice.FieldValue("routeInfo") RouteInfo routeInfo,
        @Advice.FieldValue("clazz") Class<?> request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {

      Context parentContext = Java8BytecodeBridge.currentContext();
      updateServerSpanName(parentContext, routeInfo);

      if (!instrumenter().shouldStart(parentContext, request)) {
        return;
      }

      context = instrumenter().start(parentContext, request);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void setupCallback(
        @Advice.Thrown Throwable throwable,
        @Advice.Return Some<Future<Response>> responseOption,
        @Advice.FieldValue("clazz") Class<?> request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {

      if (scope == null) {
        return;
      }
      scope.close();

      if (throwable != null) {
        instrumenter().end(context, request, null, throwable);
      } else {
        responseOption.get().addEventListener(new FinatraResponseListener(context, request));
      }
    }
  }
}
