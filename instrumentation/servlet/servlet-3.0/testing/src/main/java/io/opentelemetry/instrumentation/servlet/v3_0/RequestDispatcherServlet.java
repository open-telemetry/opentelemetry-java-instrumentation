/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v3_0;

import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class RequestDispatcherServlet {

  @WebServlet(asyncSupported = true)
  public static class Forward extends HttpServlet {
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {
      String target = getTargetSafely(req);
      ServletContext context = getServletContext();
      RequestDispatcher dispatcher = context.getRequestDispatcher(target);
      dispatcher.forward(req, resp);
    }
  }

  @WebServlet(asyncSupported = true)
  public static class Include extends HttpServlet {
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {
      String target = getTargetSafely(req);
      ServletContext context = getServletContext();
      RequestDispatcher dispatcher = context.getRequestDispatcher(target);
      // for HTML test case, set the content type before calling include because
      // setContentType will be rejected if called inside include
      // check
      // https://docs.oracle.com/javaee/7/api/javax/servlet/RequestDispatcher.html#include-javax.servlet.ServletRequest-javax.servlet.ServletResponse-
      if ("/htmlPrintWriter".equals(target) || "/htmlServletOutputStream".equals(target)) {
        resp.setContentType("text/html");
      }
      dispatcher.include(req, resp);
    }
  }

  // Explicit allowlist of endpoints that may be used as dispatch targets.
  // Iterating this list and returning the path from the list element (not from user
  // input) ensures the forwarded path is never tainted by the incoming request.
  private static final List<ServerEndpoint> DISPATCH_TARGETS =
      Arrays.asList(
          ServerEndpoint.SUCCESS,
          ServerEndpoint.REDIRECT,
          ServerEndpoint.ERROR,
          ServerEndpoint.EXCEPTION,
          ServerEndpoint.QUERY_PARAM,
          ServerEndpoint.AUTH_REQUIRED,
          ServerEndpoint.CAPTURE_HEADERS,
          ServerEndpoint.CAPTURE_PARAMETERS,
          ServerEndpoint.INDEXED_CHILD,
          AbstractServlet3Test.HTML_PRINT_WRITER,
          AbstractServlet3Test.HTML_SERVLET_OUTPUT_STREAM);

  private static String getTargetSafely(HttpServletRequest req) {
    String target = req.getServletPath().replace("/dispatch", "");
    for (ServerEndpoint endpoint : DISPATCH_TARGETS) {
      if (endpoint.getPath().equals(target)) {
        return endpoint.getPath();
      }
    }
    throw new IllegalStateException("Unexpected endpoint: " + target);
  }

  private RequestDispatcherServlet() {}
}
