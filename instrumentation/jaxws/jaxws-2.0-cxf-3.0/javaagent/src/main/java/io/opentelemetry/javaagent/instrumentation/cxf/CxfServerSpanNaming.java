/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cxf;

import io.opentelemetry.instrumentation.api.servlet.ServerSpanNameSupplier;
import io.opentelemetry.javaagent.bootstrap.servlet.ServletContextPath;
import javax.servlet.http.HttpServletRequest;

public class CxfServerSpanNaming {

  public static final ServerSpanNameSupplier<CxfRequest> SERVER_SPAN_NAME =
      (context, cxfRequest) -> {
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
      };

  private CxfServerSpanNaming() {}
}
