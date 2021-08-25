/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cxf;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.servlet.ServletContextPath;
import java.util.function.Supplier;
import javax.servlet.http.HttpServletRequest;

public class CxfServerSpanNaming {

  private CxfServerSpanNaming() {}

  public static Supplier<String> getServerSpanNameSupplier(Context context, CxfRequest request) {
    return () -> getServerSpanName(context, request);
  }

  private static String getServerSpanName(Context context, CxfRequest cxfRequest) {
    String spanName = cxfRequest.spanName();
    if (spanName == null) {
      return null;
    }

    HttpServletRequest request = (HttpServletRequest) cxfRequest.message().get("HTTP.REQUEST");
    if (request != null) {
      String servletPath = request.getServletPath();
      if (!servletPath.isEmpty()) {
        spanName = servletPath + "/" + spanName;
      }
    }

    return ServletContextPath.prepend(context, spanName);
  }
}
