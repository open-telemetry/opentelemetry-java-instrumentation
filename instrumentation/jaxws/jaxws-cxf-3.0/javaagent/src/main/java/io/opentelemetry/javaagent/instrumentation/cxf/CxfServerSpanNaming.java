/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cxf;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.LocalRootSpan;
import io.opentelemetry.javaagent.bootstrap.servlet.ServletContextPath;
import io.opentelemetry.javaagent.tooling.muzzle.NoMuzzle;

public final class CxfServerSpanNaming {
  private static final Class<?> JAVAX_SERVLET_REQUEST =
      loadClass("javax.servlet.http.HttpServletRequest");
  private static final Class<?> JAKARTA_SERVLET_REQUEST =
      loadClass("jakarta.servlet.http.HttpServletRequest");

  private static Class<?> loadClass(String className) {
    try {
      return Class.forName(className);
    } catch (ClassNotFoundException exception) {
      return null;
    }
  }

  public static void updateServerSpanName(Context context, CxfRequest cxfRequest) {
    String spanName = cxfRequest.spanName();
    if (spanName == null) {
      return;
    }

    Span serverSpan = LocalRootSpan.fromContextOrNull(context);
    if (serverSpan == null) {
      return;
    }

    Object request = cxfRequest.message().get("HTTP.REQUEST");
    if (request != null) {
      String servletPath = null;
      if (JAVAX_SERVLET_REQUEST != null && JAVAX_SERVLET_REQUEST.isInstance(request)) {
        servletPath = getJavaxServletPath(request);
      } else if (JAKARTA_SERVLET_REQUEST != null && JAKARTA_SERVLET_REQUEST.isInstance(request)) {
        servletPath = getJakartaServletPath(request);
      }
      if (servletPath != null && !servletPath.isEmpty()) {
        spanName = servletPath + "/" + spanName;
      }
    }

    serverSpan.updateName(ServletContextPath.prepend(context, spanName));
  }

  @NoMuzzle
  private static String getJavaxServletPath(Object request) {
    return ((javax.servlet.http.HttpServletRequest) request).getServletPath();
  }

  @NoMuzzle
  private static String getJakartaServletPath(Object request) {
    return ((jakarta.servlet.http.HttpServletRequest) request).getServletPath();
  }

  private CxfServerSpanNaming() {}
}
