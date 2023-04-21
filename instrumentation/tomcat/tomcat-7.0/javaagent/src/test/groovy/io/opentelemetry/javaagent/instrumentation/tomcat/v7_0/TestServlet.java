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

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_PARAMETERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT;

public class TestServlet extends HttpServlet {

  @Override
  protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String path = req.getServletPath();

    ServerEndpoint serverEndpoint = ServerEndpoint.forPath(path);
    if (serverEndpoint != null) {
      HttpServerTest.controller(
          serverEndpoint,
          () -> {
            if (EXCEPTION.equals(serverEndpoint)) {
              throw new Exception(serverEndpoint.getBody());
            }
            if (CAPTURE_HEADERS.equals(serverEndpoint)) {
              resp.setHeader("X-Test-Response", req.getHeader("X-Test-Request"));
            }
            if (CAPTURE_PARAMETERS.equals(serverEndpoint)) {
              req.setCharacterEncoding("UTF8");
              String value = req.getParameter("test-parameter");
              if (!"test value õäöü".equals(value)) {
                throw new ServletException(
                    "request parameter does not have expected value " + value);
              }
            }
            if (INDEXED_CHILD.equals(serverEndpoint)) {
              ServerEndpoint.INDEXED_CHILD.collectSpanAttributes(req::getParameter);
            }
            resp.getWriter().print(serverEndpoint.getBody());
            if (REDIRECT.equals(serverEndpoint)) {
              resp.sendRedirect(serverEndpoint.getBody());
            } else if (ERROR.equals(serverEndpoint)) {
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
