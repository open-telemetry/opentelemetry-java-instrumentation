/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.axis2;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.LocalRootSpan;
import io.opentelemetry.javaagent.bootstrap.servlet.ServletContextPath;
import io.opentelemetry.javaagent.tooling.muzzle.NoMuzzle;
import org.apache.axis2.jaxws.core.MessageContext;

public final class Axis2ServerSpanNaming {
  private static final Class<?> JAVAX_REQUEST = loadClass("javax.servlet.http.HttpServletRequest");
  private static final Class<?> JAKARTA_REQUEST =
      loadClass("jakarta.servlet.http.HttpServletRequest");

  public static void updateServerSpan(Context context, Axis2Request axis2Request) {
    Span serverSpan = LocalRootSpan.fromContextOrNull(context);
    if (serverSpan == null) {
      return;
    }

    String spanName = axis2Request.spanName();
    MessageContext message = axis2Request.message();
    Object request = message.getMEPContext().get("transport.http.servletRequest");
    if (request != null) {
      String servletPath = getServletPath(request);
      if (servletPath != null && !servletPath.isEmpty()) {
        spanName = servletPath + "/" + spanName;
      }
    }

    serverSpan.updateName(ServletContextPath.prepend(context, spanName));
  }

  private static Class<?> loadClass(String name) {
    try {
      return Class.forName(name);
    } catch (ClassNotFoundException exception) {
      return null;
    }
  }

  @NoMuzzle
  private static String getServletPath(Object request) {
    if (JAVAX_REQUEST != null && JAVAX_REQUEST.isInstance(request)) {
      return ((javax.servlet.http.HttpServletRequest) request).getServletPath();
    } else if (JAKARTA_REQUEST != null && JAKARTA_REQUEST.isInstance(request)) {
      return ((jakarta.servlet.http.HttpServletRequest) request).getServletPath();
    }
    return null;
  }

  private Axis2ServerSpanNaming() {}
}
