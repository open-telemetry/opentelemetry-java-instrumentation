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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("unused")
public class Tomcat7ServerHandlerAdvice {
  public static final Logger log = LoggerFactory.getLogger(Tomcat7ServerHandlerAdvice.class);

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(
      @Advice.Argument(0) Request request,
      @Advice.Local("otelContext") Context context,
      @Advice.Local("otelScope") Scope scope) {
    try {
      Context attachedContext = tracer().getServerContext(request);
      if (attachedContext != null) {
        log.debug(
            "Unexpected context found before server handler even started: {}", attachedContext);
        return;
      }

      context = tracer().startServerSpan(request);

      scope = context.makeCurrent();
    } catch (Throwable e) {
      e.printStackTrace();
    }
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Argument(0) Request request,
      @Advice.Argument(1) Response response,
      @Advice.Thrown Throwable throwable,
      @Advice.Local("otelContext") Context context,
      @Advice.Local("otelScope") Scope scope) {

    try {
      TomcatServerHandlerAdviceHelper.stopSpan(
          tracer(),
          Servlet3HttpServerTracer.tracer(),
          request,
          response,
          throwable,
          context,
          scope);
    } catch (Throwable e) {
      e.printStackTrace();
    }
  }
}
