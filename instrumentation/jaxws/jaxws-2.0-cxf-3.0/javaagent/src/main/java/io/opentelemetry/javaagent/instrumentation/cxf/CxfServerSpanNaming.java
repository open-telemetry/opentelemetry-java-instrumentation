/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cxf;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.LocalRootSpan;
import io.opentelemetry.javaagent.bootstrap.servlet.ServletContextPath;
import javax.servlet.http.HttpServletRequest;

public final class CxfServerSpanNaming {

  public static void updateServerSpanName(Context context, CxfRequest cxfRequest) {
    String spanName = cxfRequest.spanName();
    if (spanName == null) {
      return;
    }

    Span serverSpan = LocalRootSpan.fromContextOrNull(context);
    if (serverSpan == null) {
      return;
    }

    HttpServletRequest request = (HttpServletRequest) cxfRequest.message().get("HTTP.REQUEST");
    if (request != null) {
      String servletPath = request.getServletPath();
      if (!servletPath.isEmpty()) {
        spanName = servletPath + "/" + spanName;
      }
    }

    serverSpan.updateName(ServletContextPath.prepend(context, spanName));
  }

  private CxfServerSpanNaming() {}
}
