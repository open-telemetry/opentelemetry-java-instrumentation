/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.metro;

import com.sun.xml.ws.api.message.Packet;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.ServerSpan;
import io.opentelemetry.javaagent.bootstrap.servlet.ServletContextPath;
import javax.servlet.http.HttpServletRequest;
import javax.xml.ws.handler.MessageContext;

public final class MetroServerSpanNaming {

  public static void updateServerSpanName(Context context, MetroRequest metroRequest) {
    String spanName = metroRequest.spanName();
    if (spanName == null) {
      return;
    }

    Span serverSpan = ServerSpan.fromContextOrNull(context);
    if (serverSpan == null) {
      return;
    }

    Packet packet = metroRequest.packet();
    HttpServletRequest request = (HttpServletRequest) packet.get(MessageContext.SERVLET_REQUEST);
    if (request != null) {
      String servletPath = request.getServletPath();
      if (!servletPath.isEmpty()) {
        String pathInfo = request.getPathInfo();
        if (pathInfo != null) {
          spanName = servletPath + "/" + spanName;
        } else {
          // when pathInfo is null then there is a servlet that is mapped to this exact service
          // servletPath already contains the service name
          String operationName = packet.getWSDLOperation().getLocalPart();
          spanName = servletPath + "/" + operationName;
        }
      }
    }

    serverSpan.updateName(ServletContextPath.prepend(context, spanName));
  }

  private MetroServerSpanNaming() {}
}
