/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.v10_0;

import io.opentelemetry.instrumentation.test.base.HttpServerTest;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public class TestServlet extends HttpServlet {

  @Override
  protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String path = req.getServletPath();

    HttpServerTest.ServerEndpoint serverEndpoint = HttpServerTest.ServerEndpoint.forPath(path);
    if (serverEndpoint != null) {
      HttpServerTest.controller(
          serverEndpoint,
          () -> {
            if (serverEndpoint == HttpServerTest.ServerEndpoint.EXCEPTION) {
              throw new Exception(serverEndpoint.getBody());
            }
            if (serverEndpoint == HttpServerTest.ServerEndpoint.CAPTURE_HEADERS) {
              resp.setHeader("X-Test-Response", req.getHeader("X-Test-Request"));
            }
            if (serverEndpoint == HttpServerTest.ServerEndpoint.INDEXED_CHILD) {
              HttpServerTest.ServerEndpoint.INDEXED_CHILD.collectSpanAttributes(req::getParameter);
            }
            resp.getWriter().print(serverEndpoint.getBody());
            if (serverEndpoint == HttpServerTest.ServerEndpoint.REDIRECT) {
              resp.sendRedirect(serverEndpoint.getBody());
            } else if (serverEndpoint == HttpServerTest.ServerEndpoint.ERROR) {
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
