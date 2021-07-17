/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.v7_0;

import static io.opentelemetry.javaagent.instrumentation.tomcat.v7_0.Tomcat7Tracer.tracer;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.servlet.v3_0.Servlet3HttpServerTracer;
import io.opentelemetry.javaagent.instrumentation.tomcat.common.TomcatServerHandlerAdviceHelper;
import net.bytebuddy.asm.Advice;
import org.apache.coyote.Request;
import org.apache.coyote.Response;

@SuppressWarnings("unused")
public class Tomcat7ServerHandlerAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(
      @Advice.Argument(0) Request request,
      @Advice.Argument(1) Response response,
      @Advice.Local("otelContext") Context context,
      @Advice.Local("otelScope") Scope scope) {
    if (!tracer().shouldStartSpan(request)) {
      return;
    }

    context = tracer().startServerSpan(request);

    scope = context.makeCurrent();

    TomcatServerHandlerAdviceHelper.attachResponseToRequest(
        Tomcat7ServletEntityProvider.INSTANCE,
        Servlet3HttpServerTracer.tracer(),
        request,
        response);
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Argument(0) Request request,
      @Advice.Argument(1) Response response,
      @Advice.Thrown Throwable throwable,
      @Advice.Local("otelContext") Context context,
      @Advice.Local("otelScope") Scope scope) {

    TomcatServerHandlerAdviceHelper.stopSpan(
        tracer(),
        Tomcat7ServletEntityProvider.INSTANCE,
        Servlet3HttpServerTracer.tracer(),
        request,
        response,
        throwable,
        context,
        scope);
  }
}
