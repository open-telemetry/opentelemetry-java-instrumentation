/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.common;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.servlet.ServletHttpServerTracer;
import io.opentelemetry.instrumentation.servlet.TagSettingAsyncListener;
import java.util.concurrent.atomic.AtomicBoolean;
import net.bytebuddy.asm.Advice;
import org.apache.coyote.Request;
import org.apache.coyote.Response;

public class TomcatServerHandlerAdviceHelper {
  /**
   * Shared stop method used by advices for different Tomcat versions.
   *
   * @param tracer Tracer for non-async path (uses Tomcat Coyote request/response)
   * @param servletTracer Tracer for async path (uses servlet request/response)
   * @param request Tomcat Coyote request object
   * @param response Tomcat Coyote request object
   * @param <REQUEST> HttpServletRequest class
   * @param <RESPONSE> HttpServletResponse class
   */
  public static <REQUEST, RESPONSE> void stopSpan(
      TomcatTracer tracer,
      ServletHttpServerTracer<REQUEST, RESPONSE> servletTracer,
      Request request,
      Response response,
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
        tracer.endExceptionally(context, throwable, response);
      } else {
        // If the response is not committed, then response headers, including response code, are
        // not yet written to the output stream.
        tracer.endExceptionally(context, throwable);
      }
      return;
    }

    if (response.isCommitted()) {
      tracer.end(context, response);
      return;
    }

    Object note = request.getNote(1);
    if (note instanceof org.apache.catalina.connector.Request) {
      AtomicBoolean responseHandled = new AtomicBoolean(false);

      org.apache.catalina.connector.Request servletRequest =
          (org.apache.catalina.connector.Request) note;
      if (servletRequest.isAsync()) {
        try {
          // The Catalina Request always implements the HttpServletRequest (either javax or jakarta)
          // which is also what REQUEST generic parameter is. We cannot cast normally without a
          // generic because this class is compiled against javax.servlet which would make it try
          // to use request from javax.servlet when REQUEST is actually from jakarta.servlet.
          //noinspection unchecked
          servletTracer
              .getServletAccessor()
              .addRequestAsyncListener(
                  (REQUEST) servletRequest,
                  new TagSettingAsyncListener<>(servletTracer, responseHandled, context));
        } catch (IllegalStateException e) {
          // thrown by newer tomcats if request was already handled while setting the listener.
          tracer.end(context, response);
          return;
        }
      }

      // In case the request finished before adding the listener on older tomcats...
      if (!servletRequest.isAsyncStarted() && responseHandled.compareAndSet(false, true)) {
        tracer.end(context, response);
      }
    }
  }
}
