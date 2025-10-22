/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.restlet.v2_0;

import static io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteSource.CONTROLLER;
import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.restlet.v2_0.RestletSingletons.instrumenter;
import static io.opentelemetry.javaagent.instrumentation.restlet.v2_0.RestletSingletons.serverSpanName;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRoute;
import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseCustomizerHolder;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
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
    public static class AdviceScope {
      private final Context context;
      private final Scope scope;

      private AdviceScope(Context context, Scope scope) {
        this.context = context;
        this.scope = scope;
      }

      @Nullable
      public static AdviceScope start(Request request) {
        Context parentContext = currentContext();

        if (!instrumenter().shouldStart(parentContext, request)) {
          return null;
        }

        Context context = instrumenter().start(parentContext, request);
        return new AdviceScope(context, context.makeCurrent());
      }

      public void end(Throwable exception, Request request, Response response) {
        scope.close();

        if (Status.CLIENT_ERROR_NOT_FOUND.equals(response.getStatus())) {
          HttpServerRoute.update(context, CONTROLLER, serverSpanName(), "/*");
        }

        HttpServerResponseCustomizerHolder.getCustomizer()
            .customize(context, response, RestletResponseMutator.INSTANCE);

        if (exception != null) {
          instrumenter().end(context, request, response, exception);
          return;
        }

        // Restlet suppresses exceptions and sets the throwable in status
        Throwable statusThrowable = response.getStatus().getThrowable();

        instrumenter().end(context, request, response, statusThrowable);
      }
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope beginRequest(
        @Advice.Argument(0) Request request, @Advice.Argument(1) Response response) {
      return AdviceScope.start(request);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void finishRequest(
        @Advice.Argument(0) Request request,
        @Advice.Argument(1) Response response,
        @Advice.Thrown @Nullable Throwable exception,
        @Advice.Enter @Nullable AdviceScope adviceScope) {

      if (adviceScope != null) {
        adviceScope.end(exception, request, response);
      }
    }
  }
}
