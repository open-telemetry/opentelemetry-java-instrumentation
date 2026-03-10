/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finatra;

import static io.opentelemetry.javaagent.instrumentation.finatra.FinatraSingletons.getCallbackClass;
import static io.opentelemetry.javaagent.instrumentation.finatra.FinatraSingletons.instrumenter;
import static io.opentelemetry.javaagent.instrumentation.finatra.FinatraSingletons.setCallbackClass;
import static io.opentelemetry.javaagent.instrumentation.finatra.FinatraSingletons.updateServerSpanName;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.twitter.finagle.http.Response;
import com.twitter.finatra.http.contexts.RouteInfo;
import com.twitter.finatra.http.internal.routing.Route;
import com.twitter.util.Future;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import scala.Some;

public class FinatraRouteInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.twitter.finatra.http.internal.routing.Route");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("handleMatch")
            .and(takesArguments(2))
            .and(takesArgument(0, named("com.twitter.finagle.http.Request"))),
        this.getClass().getName() + "$HandleMatchAdvice");
    transformer.applyAdviceToMethod(
        named("copy").and(returns(named("com.twitter.finatra.http.internal.routing.Route"))),
        this.getClass().getName() + "$CopyAdvice");
  }

  @SuppressWarnings("unused")
  public static class HandleMatchAdvice {

    public static class AdviceScope {
      private final FinatraRequest request;
      private final Context context;
      private final Scope scope;

      private AdviceScope(FinatraRequest request, Context context, Scope scope) {
        this.request = request;
        this.context = context;
        this.scope = scope;
      }

      @Nullable
      public static AdviceScope start(Route route, RouteInfo routeInfo, Class<?> controllerClass) {

        Context parentContext = Context.current();
        updateServerSpanName(parentContext, routeInfo);

        Class<?> callbackClass = getCallbackClass(route);
        // We expect callback to be an inner class of the controller class. If it is not we are not
        // going to record it at all.
        FinatraRequest request;
        if (callbackClass != null) {
          request = FinatraRequest.create(controllerClass, callbackClass, "apply");
        } else {
          request = FinatraRequest.create(controllerClass);
        }

        if (!instrumenter().shouldStart(parentContext, request)) {
          return null;
        }
        Context context = instrumenter().start(parentContext, request);
        return new AdviceScope(request, context, context.makeCurrent());
      }

      public void end(
          @Nullable Throwable throwable, @Nullable Some<Future<Response>> responseOption) {
        scope.close();
        if (throwable != null || responseOption == null) {
          instrumenter().end(context, request, null, throwable);
        } else {
          responseOption.get().addEventListener(new FinatraResponseListener(context, request));
        }
      }
    }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope nameSpan(
        @Advice.This Route route,
        @Advice.FieldValue("routeInfo") RouteInfo routeInfo,
        @Advice.FieldValue("clazz") Class<?> controllerClass) {
      return AdviceScope.start(route, routeInfo, controllerClass);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void setupCallback(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Return @Nullable Some<Future<Response>> responseOption,
        @Advice.Enter @Nullable AdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.end(throwable, responseOption);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class CopyAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(@Advice.This Route route, @Advice.Return Route result) {
      setCallbackClass(result, getCallbackClass(route));
    }
  }
}
