/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.axis2;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.servlet.ServletContextPath;
import java.util.function.Supplier;
import javax.servlet.http.HttpServletRequest;
import org.apache.axis2.jaxws.core.MessageContext;

public class Axis2ServerSpanNaming {

  private Axis2ServerSpanNaming() {}

  public static Supplier<String> getServerSpanNameSupplier(Context context, Axis2Request request) {
    return () -> getServerSpanName(context, request);
  }

  private static String getServerSpanName(Context context, Axis2Request axis2Request) {
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
  }
}
