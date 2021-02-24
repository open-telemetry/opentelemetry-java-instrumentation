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
      HttpServerTest.controller(
          serverEndpoint,
          () -> {
            if (serverEndpoint == HttpServerTest.ServerEndpoint.EXCEPTION) {
              throw new Exception(serverEndpoint.getBody());
            }
            resp.getWriter().print(serverEndpoint.getBody());
            if (serverEndpoint == HttpServerTest.ServerEndpoint.REDIRECT) {
              resp.sendRedirect(serverEndpoint.getBody());
            } else if (serverEndpoint == HttpServerTest.ServerEndpoint.ERROR) {
              resp.sendError(serverEndpoint.getStatus());
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
