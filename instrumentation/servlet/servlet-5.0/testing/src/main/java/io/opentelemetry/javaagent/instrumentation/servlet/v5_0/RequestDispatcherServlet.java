/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0;

import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import jakarta.servlet.RequestDispatcher;
import java.util.Arrays;
import java.util.List;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public class RequestDispatcherServlet {

  @WebServlet(asyncSupported = true)
  public static class Forward extends HttpServlet {
    private static final long serialVersionUID = 1L;

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
    private static final long serialVersionUID = 1L;

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {
      String target = getTargetSafely(req);
      ServletContext context = getServletContext();
      RequestDispatcher dispatcher = context.getRequestDispatcher(target);
      // For the HTML test cases, set the content type before include() because the response is
      // already committed for header updates inside the included resource.
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
          AbstractServlet5Test.HTML_PRINT_WRITER,
          AbstractServlet5Test.HTML_SERVLET_OUTPUT_STREAM);

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
