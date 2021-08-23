/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.restlet.v1_1;

import static io.opentelemetry.instrumentation.restlet.v1_1.RestletHttpServerTracer.tracer;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.restlet.data.Request;
import org.restlet.data.Response;

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
            .and(takesArgument(0, named("org.restlet.data.Request")))
            .and(takesArgument(1, named("org.restlet.data.Response"))),
        this.getClass().getName() + "$ServerHandleAdvice");
  }

  @SuppressWarnings("unused")
  public static class ServerHandleAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void beginRequest(
        @Advice.Argument(0) Request request,
        @Advice.Argument(1) Response response,
        @Advice.Local("otelScope") Scope scope) {

      Context serverContext = tracer().getServerContext(request);

      if (serverContext != null) {
        return;
      }

      serverContext =
          tracer().startSpan(request, request, request, request.getResourceRef().getPath());
      scope = serverContext.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void finishRequest(
        @Advice.Argument(0) Request request,
        @Advice.Argument(1) Response response,
        @Advice.Thrown Throwable exception,
        @Advice.Local("otelScope") Scope scope) {

      Context serverContext = tracer().getServerContext(request);

      if (scope == null) {
        return;
      }

      scope.close();

      if (serverContext == null) {
        return;
      }

      if (exception != null) {
        tracer().endExceptionally(serverContext, exception, response);
        return;
      }

      // Restlet suppresses exceptions and sets the throwable in status
      Throwable statusThrowable = response.getStatus().getThrowable();

      if (statusThrowable != null) {
        tracer().endExceptionally(serverContext, statusThrowable, response);
        return;
      }

      tracer().end(serverContext, response);
    }
  }
}
