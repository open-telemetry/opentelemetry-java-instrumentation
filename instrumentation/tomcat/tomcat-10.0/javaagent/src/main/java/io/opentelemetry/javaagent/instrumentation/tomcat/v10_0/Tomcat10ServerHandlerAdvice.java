/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.v10_0;

import static io.opentelemetry.javaagent.instrumentation.tomcat.v10_0.Tomcat10Tracer.tracer;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.servlet.jakarta.v5_0.JakartaServletHttpServerTracer;
import io.opentelemetry.javaagent.instrumentation.tomcat.common.TomcatServerHandlerAdviceHelper;
import net.bytebuddy.asm.Advice;
import org.apache.coyote.Request;
import org.apache.coyote.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("unused")
public class Tomcat10ServerHandlerAdvice {
  public static final Logger log = LoggerFactory.getLogger(Tomcat10ServerHandlerAdvice.class);

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(
      @Advice.Argument(0) Request request,
      @Advice.Local("otelContext") Context context,
      @Advice.Local("otelScope") Scope scope) {
    Context attachedContext = tracer().getServerContext(request);
    if (attachedContext != null) {
      log.debug("Unexpected context found before server handler even started: {}", attachedContext);
      return;
    }

    context = tracer().startServerSpan(request);

    scope = context.makeCurrent();
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
        JakartaServletHttpServerTracer.tracer(),
        request,
        response,
        throwable,
        context,
        scope);
  }
}
