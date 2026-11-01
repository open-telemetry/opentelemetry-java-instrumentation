/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.v10_0;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

class TestServlet extends HttpServlet {

  private static final boolean TRACE_ID_REQUEST_ATTRIBUTE_ENABLED =
      Boolean.getBoolean(
          "otel.instrumentation.servlet.experimental.trace-id-request-attribute.enabled");

  @Override
  protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String path = req.getServletPath();

    assertThat(req.getAttribute("trace_id"))
        .isEqualTo(
            TRACE_ID_REQUEST_ATTRIBUTE_ENABLED
                ? Span.current().getSpanContext().getTraceId()
                : null);
    assertThat(req.getAttribute("span_id"))
        .isEqualTo(
            TRACE_ID_REQUEST_ATTRIBUTE_ENABLED
                ? Span.current().getSpanContext().getSpanId()
                : null);

    ServerEndpoint serverEndpoint = ServerEndpoint.forPath(path);
    if (serverEndpoint != null) {
      AbstractHttpServerTest.controller(
          serverEndpoint,
          () -> {
            if (serverEndpoint == ServerEndpoint.EXCEPTION) {
              throw new IllegalStateException(serverEndpoint.getBody());
            }
            if (serverEndpoint == ServerEndpoint.CAPTURE_HEADERS) {
              resp.setHeader("X-Test-Response", req.getHeader("X-Test-Request"));
            }
            if (serverEndpoint == ServerEndpoint.CAPTURE_PARAMETERS) {
              req.setCharacterEncoding("UTF8");
              String value = req.getParameter("test-parameter");
              if (!"test value õäöü".equals(value)) {
                throw new IllegalStateException(
                    "request parameter does not have expected value " + value);
              }
            }
            if (serverEndpoint == ServerEndpoint.INDEXED_CHILD) {
              ServerEndpoint.INDEXED_CHILD.collectSpanAttributes(req::getParameter);
            }
            String responseBody = serverEndpoint.getBody();
            if (serverEndpoint == ServerEndpoint.INDEXED_CHILD_FROM_REQUEST_BODY) {
              responseBody = AbstractHttpServerTest.readRequestBody(req.getInputStream());
              AbstractHttpServerTest.bodyConsumer(serverEndpoint, responseBody);
            }
            if (serverEndpoint == ServerEndpoint.REDIRECT) {
              resp.sendRedirect(responseBody);
              return null;
            }
            if (serverEndpoint == ServerEndpoint.ERROR) {
              resp.sendError(serverEndpoint.getStatus(), responseBody);
              return null;
            }
            resp.getWriter().print(responseBody);
            resp.setStatus(serverEndpoint.getStatus());
            return null;
          });
    } else {
      resp.getWriter().println("No cookie for you: " + path);
      resp.setStatus(400);
    }
  }
}
