/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.axis2;

import io.opentelemetry.instrumentation.api.servlet.ServerSpanNameSupplier;
import io.opentelemetry.instrumentation.api.servlet.ServletContextPath;
import javax.servlet.http.HttpServletRequest;
import org.apache.axis2.jaxws.core.MessageContext;

public class Axis2ServerSpanNaming {

  public static final ServerSpanNameSupplier<Axis2Request> SERVER_SPAN_NAME =
      (context, axis2Request) -> {
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

        return ServletContextPath.prepend(context, spanName);
      };

  private Axis2ServerSpanNaming() {}
}
