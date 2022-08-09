/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.restlet.v2_0;

import static io.opentelemetry.instrumentation.api.instrumenter.http.HttpRouteSource.CONTROLLER;
import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.restlet.v2_0.RestletSingletons.instrumenter;
import static io.opentelemetry.javaagent.instrumentation.restlet.v2_0.RestletSingletons.serverSpanName;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpRouteHolder;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.Status;

public class ServerInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.restlet.Server");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("handle"))
            .and(takesArgument(0, named("org.restlet.Request")))
            .and(takesArgument(1, named("org.restlet.Response"))),
        this.getClass().getName() + "$ServerHandleAdvice");
  }

  @SuppressWarnings("unused")
  public static class ServerHandleAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void beginRequest(
        @Advice.Argument(0) Request request,
        @Advice.Argument(1) Response response,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {

      Context parentContext = currentContext();

      if (!instrumenter().shouldStart(parentContext, request)) {
        return;
      }

      context = instrumenter().start(parentContext, request);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void finishRequest(
        @Advice.Argument(0) Request request,
        @Advice.Argument(1) Response response,
        @Advice.Thrown Throwable exception,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {

      if (scope == null) {
        return;
      }

      scope.close();

      if (Status.CLIENT_ERROR_NOT_FOUND.equals(response.getStatus())) {
        HttpRouteHolder.updateHttpRoute(context, CONTROLLER, serverSpanName(), "/*");
      }

      if (exception != null) {
        instrumenter().end(context, request, response, exception);
        return;
      }

      // Restlet suppresses exceptions and sets the throwable in status
      Throwable statusThrowable = response.getStatus().getThrowable();

      instrumenter().end(context, request, response, statusThrowable);
    }
  }
}
