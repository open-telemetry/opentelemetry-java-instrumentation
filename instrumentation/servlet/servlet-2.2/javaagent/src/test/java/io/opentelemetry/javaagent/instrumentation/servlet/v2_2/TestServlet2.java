/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v2_2;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD;

import io.opentelemetry.instrumentation.test.base.HttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class TestServlet2 {
  public static class Sync extends HttpServlet {
    @Override
    protected void service(final HttpServletRequest req, final HttpServletResponse resp) {
      req.getRequestDispatcher(null);
      final ServerEndpoint endpoint = ServerEndpoint.forPath(req.getServletPath());
      HttpServerTest.controller(
          endpoint,
          () -> {
            resp.setContentType("text/plain");
            switch (endpoint.name()) {
              case "SUCCESS":
                resp.setStatus(endpoint.getStatus());
                resp.getWriter().print(endpoint.getBody());
                break;
              case "QUERY_PARAM":
                resp.setStatus(endpoint.getStatus());
                resp.getWriter().print(req.getQueryString());
                break;
              case "REDIRECT":
                resp.sendRedirect(endpoint.getBody());
                break;
              case "ERROR":
                resp.sendError(endpoint.getStatus(), endpoint.getBody());
                break;
              case "EXCEPTION":
                throw new Exception(endpoint.getBody());
              case "INDEXED_CHILD":
                INDEXED_CHILD.collectSpanAttributes(req::getParameter);
                resp.setStatus(endpoint.getStatus());
                resp.getWriter().print(endpoint.getBody());
                break;
            }
            return null;
          });
    }
  }
}
