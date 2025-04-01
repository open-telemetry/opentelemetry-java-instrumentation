/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.v10_0;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;

import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.CountDownLatch;

@WebServlet(asyncSupported = true)
class AsyncServlet extends HttpServlet {
  @Override
  protected void service(HttpServletRequest req, HttpServletResponse resp) {
    ServerEndpoint endpoint = ServerEndpoint.forPath(req.getServletPath());
    CountDownLatch latch = new CountDownLatch(1);
    AsyncContext context = req.startAsync();
    if (endpoint == EXCEPTION) {
      context.setTimeout(5000);
    }
    context.start(
        () -> {
          try {
            AbstractHttpServerTest.controller(
                endpoint,
                () -> {
                  resp.setContentType("text/plain");
                  if (endpoint.equals(SUCCESS) || endpoint.equals(ERROR)) {
                    resp.setStatus(endpoint.getStatus());
                    resp.getWriter().print(endpoint.getBody());
                  } else if (endpoint.equals(INDEXED_CHILD)) {
                    endpoint.collectSpanAttributes(req::getParameter);
                    resp.setStatus(endpoint.getStatus());
                    resp.getWriter().print(endpoint.getBody());
                  } else if (endpoint.equals(QUERY_PARAM)) {
                    resp.setStatus(endpoint.getStatus());
                    resp.getWriter().print(req.getQueryString());
                  } else if (endpoint.equals(REDIRECT)) {
                    resp.sendRedirect(endpoint.getBody());
                  } else if (endpoint.equals(CAPTURE_HEADERS)) {
                    resp.setHeader("X-Test-Response", req.getHeader("X-Test-Request"));
                    resp.setStatus(endpoint.getStatus());
                    resp.getWriter().print(endpoint.getBody());
                  } else if (endpoint.equals(EXCEPTION)) {
                    resp.setStatus(endpoint.getStatus());
                    PrintWriter writer = resp.getWriter();
                    writer.print(endpoint.getBody());
                    writer.close();
                    throw new IllegalStateException(endpoint.getBody());
                  }
                });
          } catch (IOException exception) {
            throw new IllegalStateException(exception);
          } finally {
            // complete at the end so the server span will end after the controller span
            if (endpoint != EXCEPTION) {
              context.complete();
            }
            latch.countDown();
          }
        });
    try {
      latch.await();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
