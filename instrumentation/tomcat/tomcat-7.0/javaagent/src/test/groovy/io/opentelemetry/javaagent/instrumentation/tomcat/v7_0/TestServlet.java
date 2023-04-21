/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.v7_0;

import io.opentelemetry.instrumentation.test.base.HttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class TestServlet extends HttpServlet {

  @Override
  protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String path = req.getServletPath();

    ServerEndpoint serverEndpoint = ServerEndpoint.forPath(path);
    if (serverEndpoint != null) {
      HttpServerTest.controller(
          serverEndpoint,
          () -> {
            if (serverEndpoint == ServerEndpoint.EXCEPTION) {
              throw new Exception(serverEndpoint.getBody());
            }
            if (serverEndpoint == ServerEndpoint.CAPTURE_HEADERS) {
              resp.setHeader("X-Test-Response", req.getHeader("X-Test-Request"));
            }
            if (serverEndpoint == ServerEndpoint.CAPTURE_PARAMETERS) {
              req.setCharacterEncoding("UTF8");
              String value = req.getParameter("test-parameter");
              if (!"test value õäöü".equals(value)) {
                throw new ServletException(
                    "request parameter does not have expected value " + value);
              }
            }
            if (serverEndpoint == ServerEndpoint.INDEXED_CHILD) {
              ServerEndpoint.INDEXED_CHILD.collectSpanAttributes(req::getParameter);
            }
            resp.getWriter().print(serverEndpoint.getBody());
            if (serverEndpoint == ServerEndpoint.REDIRECT) {
              resp.sendRedirect(serverEndpoint.getBody());
            } else if (serverEndpoint == ServerEndpoint.ERROR) {
              resp.sendError(serverEndpoint.getStatus(), serverEndpoint.getBody());
            } else {
              resp.setStatus(serverEndpoint.getStatus());
            }
            return null;
          });
    } else {
      resp.getWriter().println("No cookie for you: " + path);
      resp.setStatus(400);
    }
  }
}
