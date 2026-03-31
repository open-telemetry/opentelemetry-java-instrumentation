/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0;

import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import jakarta.servlet.RequestDispatcher;
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

  private static String getTargetSafely(HttpServletRequest req) {
    String target = req.getServletPath().replace("/dispatch", "");
    ServerEndpoint endpoint = ServerEndpoint.forPath(target);
    if (endpoint != null) {
      return endpoint.getPath();
    }
    throw new IllegalStateException("Unexpected endpoint: " + target);
  }

  private RequestDispatcherServlet() {}
}
