/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.common;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.servlet.ServletHttpServerTracer;
import io.opentelemetry.javaagent.instrumentation.servlet.common.service.ServletAndFilterAdviceHelper;
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
      TomcatServletEntityProvider<REQUEST, RESPONSE> servletEntityProvider,
      ServletHttpServerTracer<REQUEST, RESPONSE> servletTracer,
      Request request,
      Response response,
      Throwable throwable,
      Context context,
      Scope scope) {
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

    REQUEST servletRequest = servletEntityProvider.getServletRequest(request);

    if (servletRequest != null
        && ServletAndFilterAdviceHelper.mustEndOnHandlerMethodExit(servletTracer, servletRequest)) {
      tracer.end(context, response);
    }
  }

  /**
   * Must be attached in Tomcat instrumentations since Tomcat valves can use startAsync outside of
   * servlet scope.
   */
  public static <REQUEST, RESPONSE> void attachResponseToRequest(
      TomcatServletEntityProvider<REQUEST, RESPONSE> servletEntityProvider,
      ServletHttpServerTracer<REQUEST, RESPONSE> servletTracer,
      Request request,
      Response response) {

    REQUEST servletRequest = servletEntityProvider.getServletRequest(request);
    RESPONSE servletResponse = servletEntityProvider.getServletResponse(response);

    if (servletRequest != null && servletResponse != null) {
      servletTracer.setAsyncListenerResponse(servletRequest, servletResponse);
    }
  }
}
