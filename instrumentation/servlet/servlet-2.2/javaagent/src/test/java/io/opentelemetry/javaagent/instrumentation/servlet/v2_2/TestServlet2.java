/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v2_2;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;

import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import java.io.IOException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class TestServlet2 {

  private TestServlet2() {}

  public static class Sync extends HttpServlet {
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
      req.getRequestDispatcher(null);
      ServerEndpoint endpoint = ServerEndpoint.forPath(req.getServletPath());
      AbstractHttpServerTest.controller(
          endpoint,
          () -> {
            resp.setContentType("text/plain");
            if (SUCCESS.equals(endpoint)) {
              resp.setStatus(endpoint.getStatus());
              resp.getWriter().print(endpoint.getBody());
            } else if (QUERY_PARAM.equals(endpoint)) {
              resp.setStatus(endpoint.getStatus());
              resp.getWriter().print(req.getQueryString());
            } else if (REDIRECT.equals(endpoint)) {
              resp.sendRedirect(endpoint.getBody());
            } else if (ERROR.equals(endpoint)) {
              resp.sendError(endpoint.getStatus(), endpoint.getBody());
            } else if (EXCEPTION.equals(endpoint)) {
              throw new IllegalStateException(endpoint.getBody());
            } else if (INDEXED_CHILD.equals(endpoint)) {
              INDEXED_CHILD.collectSpanAttributes(req::getParameter);
              resp.setStatus(endpoint.getStatus());
              resp.getWriter().print(endpoint.getBody());
            }
            return null;
          });
    }
  }
}
