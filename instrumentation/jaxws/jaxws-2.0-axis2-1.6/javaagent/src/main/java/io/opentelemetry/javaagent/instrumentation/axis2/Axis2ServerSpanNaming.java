/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.axis2;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.ServerSpan;
import io.opentelemetry.javaagent.bootstrap.servlet.ServletContextPath;
import javax.servlet.http.HttpServletRequest;
import org.apache.axis2.jaxws.core.MessageContext;

public final class Axis2ServerSpanNaming {

  public static void updateServerSpan(Context context, Axis2Request axis2Request) {
    Span serverSpan = ServerSpan.fromContextOrNull(context);
    if (serverSpan == null) {
      return;
    }

    String spanName = axis2Request.spanName();
    MessageContext message = axis2Request.message();
    HttpServletRequest request =
        (HttpServletRequest) message.getMEPContext().get("transport.http.servletRequest");
    if (request != null) {
      String servletPath = request.getServletPath();
      if (!servletPath.isEmpty()) {
        spanName = servletPath + "/" + spanName;
      }
    }

    serverSpan.updateName(ServletContextPath.prepend(context, spanName));
  }

  private Axis2ServerSpanNaming() {}
}
