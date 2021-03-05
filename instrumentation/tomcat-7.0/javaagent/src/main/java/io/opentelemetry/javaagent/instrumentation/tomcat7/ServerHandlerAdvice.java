/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat7;

import static io.opentelemetry.javaagent.instrumentation.tomcat7.TomcatTracer.tracer;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.servlet.v3_0.TagSettingAsyncListener;
import java.util.concurrent.atomic.AtomicBoolean;
import net.bytebuddy.asm.Advice;
import org.apache.coyote.Request;
import org.apache.coyote.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("unused")
public class ServerHandlerAdvice {
  private static final Logger log = LoggerFactory.getLogger(ServerHandlerAdvice.class);

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
    if (scope != null) {
      scope.close();
    }

    if (context == null) {
      return;
    }

    if (throwable != null) {
      if (response.isCommitted()) {
        tracer().endExceptionally(context, throwable, response);
      } else {
        // If the response is not committed, then response headers, including response code, are
        // not yet written to the output stream.
        tracer().endExceptionally(context, throwable);
      }
      return;
    }

    if (response.isCommitted()) {
      tracer().end(context, response);
      return;
    }

    Object note = request.getNote(1);
    if (note instanceof org.apache.catalina.connector.Request) {
      AtomicBoolean responseHandled = new AtomicBoolean(false);

      org.apache.catalina.connector.Request servletRequest =
          (org.apache.catalina.connector.Request) note;
      if (servletRequest.isAsync()) {
        try {
          servletRequest
              .getAsyncContext()
              .addListener(new TagSettingAsyncListener(responseHandled, context));
        } catch (IllegalStateException e) {
          // thrown by newer tomcats if request was already handled while setting the listener.
          tracer().end(context, response);
          return;
        }
      }

      // In case the request finished before adding the listener on older tomcats...
      if (!servletRequest.isAsyncStarted() && responseHandled.compareAndSet(false, true)) {
        tracer().end(context, response);
      }
    }
  }
}
