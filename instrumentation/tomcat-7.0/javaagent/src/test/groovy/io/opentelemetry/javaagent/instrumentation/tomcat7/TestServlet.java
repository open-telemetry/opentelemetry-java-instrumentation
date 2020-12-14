/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat7;

import io.opentelemetry.instrumentation.test.base.HttpServerTest;
import java.io.IOException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class TestServlet extends HttpServlet {

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String path = req.getServletPath();

    HttpServerTest.ServerEndpoint serverEndpoint = HttpServerTest.ServerEndpoint.forPath(path);
    if (serverEndpoint != null) {
      if (serverEndpoint == HttpServerTest.ServerEndpoint.EXCEPTION) {
        HttpServerTest.controller(
            serverEndpoint,
            () -> {
              throw new Exception(serverEndpoint.getBody());
            });
      } else {
        resp.getWriter().print(HttpServerTest.controller(serverEndpoint, serverEndpoint::getBody));
      }

      if (serverEndpoint == HttpServerTest.ServerEndpoint.REDIRECT) {
        resp.sendRedirect(serverEndpoint.getBody());
      } else {
        resp.setStatus(serverEndpoint.getStatus());
      }
    } else if ("/errorPage".equals(path)) {
      resp.getWriter().print(HttpServerTest.ServerEndpoint.EXCEPTION.getBody());
      resp.setStatus(500);
    } else {
      resp.getWriter().println("No cookie for you: " + path);
      resp.setStatus(400);
    }
  }
}
