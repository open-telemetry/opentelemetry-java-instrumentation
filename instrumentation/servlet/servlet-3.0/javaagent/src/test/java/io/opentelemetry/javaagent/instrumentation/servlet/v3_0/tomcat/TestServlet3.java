/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0.tomcat;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;

import io.opentelemetry.instrumentation.test.base.HttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.CountDownLatch;
import javax.servlet.AsyncContext;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class TestServlet3 {
  @WebServlet
  public static class Sync extends HttpServlet {
    @Override
    protected void service(final HttpServletRequest req, final HttpServletResponse resp) {
      String servletPath = (String) req.getAttribute(RequestDispatcher.INCLUDE_SERVLET_PATH);
      if (servletPath == null) {
        servletPath = req.getServletPath();
      }

      final ServerEndpoint endpoint = ServerEndpoint.forPath(servletPath);
      HttpServerTest.controller(
          endpoint,
          () -> {
            resp.setContentType("text/plain");
            switch (endpoint.name()) {
              case "SUCCESS":
                resp.setStatus(endpoint.getStatus());
                resp.getWriter().print(endpoint.getBody());
                break;
              case "INDEXED_CHILD":
                endpoint.collectSpanAttributes(req::getParameter);
                resp.setStatus(endpoint.getStatus());
                break;
              case "QUERY_PARAM":
                resp.setStatus(endpoint.getStatus());
                resp.getWriter().print(req.getQueryString());
                break;
              case "REDIRECT":
                resp.sendRedirect(endpoint.getBody());
                break;
              case "CAPTURE_HEADERS":
                resp.setHeader("X-Test-Response", req.getHeader("X-Test-Request"));
                resp.setStatus(endpoint.getStatus());
                resp.getWriter().print(endpoint.getBody());
                break;
              case "CAPTURE_PARAMETERS":
                req.setCharacterEncoding("UTF8");
                String value = req.getParameter("test-parameter");
                if (!value.equals("test value õäöü")) {
                  throw new ServletException(
                      "request parameter does not have expected value " + value);
                }

                resp.setStatus(endpoint.getStatus());
                resp.getWriter().print(endpoint.getBody());
                break;
              case "ERROR":
                resp.sendError(endpoint.getStatus(), endpoint.getBody());
                break;
              case "EXCEPTION":
                throw new ServletException(endpoint.getBody());
              case "HTML_PRINT_WRITER":
                resp.setContentType("text/html");
                resp.setStatus(endpoint.getStatus());
                resp.setContentLength(endpoint.getBody().length());
                resp.getWriter().print(endpoint.getBody());
                break;
              case "HTML_SERVLET_OUTPUT_STREAM":
                resp.setContentType("text/html");
                resp.setStatus(endpoint.getStatus());
                resp.setContentLength(endpoint.getBody().length());
                resp.getOutputStream().print(endpoint.getBody());
                break;
              default:
                break;
            }
            return null;
          });
    }
  }

  @WebServlet(asyncSupported = true)
  public static class Async extends HttpServlet {
    @Override
    protected void service(final HttpServletRequest req, final HttpServletResponse resp) {
      final ServerEndpoint endpoint = ServerEndpoint.forPath(req.getServletPath());
      final CountDownLatch latch = new CountDownLatch(1);
      final AsyncContext context = req.startAsync();
      if (endpoint.equals(EXCEPTION)) {
        context.setTimeout(5000);
      }

      context.start(
          () -> {
            try {
              HttpServerTest.controller(
                  endpoint,
                  () -> {
                    resp.setContentType("text/plain");
                    switch (endpoint.name()) {
                      case "SUCCESS":
                        resp.setStatus(endpoint.getStatus());
                        resp.getWriter().print(endpoint.getBody());
                        context.complete();
                        break;
                      case "INDEXED_CHILD":
                        endpoint.collectSpanAttributes(req::getParameter);
                        resp.setStatus(endpoint.getStatus());
                        context.complete();
                        break;
                      case "QUERY_PARAM":
                        resp.setStatus(endpoint.getStatus());
                        resp.getWriter().print(req.getQueryString());
                        context.complete();
                        break;
                      case "REDIRECT":
                        resp.sendRedirect(endpoint.getBody());
                        context.complete();
                        break;
                      case "CAPTURE_HEADERS":
                        resp.setHeader("X-Test-Response", req.getHeader("X-Test-Request"));
                        resp.setStatus(endpoint.getStatus());
                        resp.getWriter().print(endpoint.getBody());
                        context.complete();
                        break;
                      case "CAPTURE_PARAMETERS":
                        req.setCharacterEncoding("UTF8");
                        String value = req.getParameter("test-parameter");
                        if (!value.equals("test value õäöü")) {
                          throw new ServletException(
                              "request parameter does not have expected value " + value);
                        }

                        resp.setStatus(endpoint.getStatus());
                        resp.getWriter().print(endpoint.getBody());
                        context.complete();
                        break;
                      case "ERROR":
                        resp.setStatus(endpoint.getStatus());
                        resp.getWriter().print(endpoint.getBody());
                        context.complete();
                        break;
                      case "EXCEPTION":
                        resp.setStatus(endpoint.getStatus());
                        PrintWriter writer = resp.getWriter();
                        writer.print(endpoint.getBody());
                        if (req.getClass().getName().contains("catalina")) {
                          writer.close();
                        }

                        throw new ServletException(endpoint.getBody());
                      case "HTML_PRINT_WRITER":
                        resp.setContentType("text/html");
                        resp.setStatus(endpoint.getStatus());
                        resp.setContentLength(endpoint.getBody().length());
                        resp.getWriter().print(endpoint.getBody());
                        context.complete();
                        break;
                      case "HTML_SERVLET_OUTPUT_STREAM":
                        resp.setContentType("text/html");
                        resp.setStatus(endpoint.getStatus());
                        resp.getOutputStream().print(endpoint.getBody());
                        context.complete();
                        break;
                      default:
                        break;
                    }
                    return null;
                  });
            } finally {
              latch.countDown();
            }
          });
    }
  }

  @WebServlet(asyncSupported = true)
  public static class FakeAsync extends HttpServlet {
    @Override
    protected void service(final HttpServletRequest req, final HttpServletResponse resp) {
      AsyncContext context = req.startAsync();
      try {
        ServerEndpoint endpoint = ServerEndpoint.forPath(req.getServletPath());

        HttpServerTest.controller(
            endpoint,
            () -> {
              resp.setContentType("text/plain");
              switch (endpoint.name()) {
                case "SUCCESS":
                  resp.setStatus(endpoint.getStatus());
                  resp.getWriter().print(endpoint.getBody());
                  break;
                case "INDEXED_CHILD":
                  endpoint.collectSpanAttributes(req::getParameter);
                  resp.setStatus(endpoint.getStatus());
                  break;
                case "QUERY_PARAM":
                  resp.setStatus(endpoint.getStatus());
                  resp.getWriter().print(req.getQueryString());
                  break;
                case "REDIRECT":
                  resp.sendRedirect(endpoint.getBody());
                  break;
                case "CAPTURE_HEADERS":
                  resp.setHeader("X-Test-Response", req.getHeader("X-Test-Request"));
                  resp.setStatus(endpoint.getStatus());
                  resp.getWriter().print(endpoint.getBody());
                  break;
                case "CAPTURE_PARAMETERS":
                  req.setCharacterEncoding("UTF8");
                  String value = req.getParameter("test-parameter");
                  if (!value.equals("test value õäöü")) {
                    throw new ServletException(
                        "request parameter does not have expected value " + value);
                  }

                  resp.setStatus(endpoint.getStatus());
                  resp.getWriter().print(endpoint.getBody());
                  break;
                case "ERROR":
                  resp.sendError(endpoint.getStatus(), endpoint.getBody());
                  break;
                case "EXCEPTION":
                  resp.setStatus(endpoint.getStatus());
                  resp.getWriter().print(endpoint.getBody());
                  throw new ServletException(endpoint.getBody());
                case "HTML_PRINT_WRITER":
                  // intentionally testing setting status before contentType here to cover that case
                  // somewhere
                  resp.setStatus(endpoint.getStatus());
                  resp.setContentType("text/html");
                  resp.getWriter().print(endpoint.getBody());
                  break;
                case "HTML_SERVLET_OUTPUT_STREAM":
                  resp.setContentType("text/html");
                  resp.setStatus(endpoint.getStatus());
                  resp.getOutputStream().print(endpoint.getBody());
                  break;
              }
              return null;
            });
      } finally {
        context.complete();
      }
    }
  }

  @WebServlet(asyncSupported = true)
  public static class DispatchImmediate extends HttpServlet {
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) {
      String target = req.getServletPath().replace("/dispatch", "");
      if (req.getQueryString() != null) {
        target += "?" + req.getQueryString();
      }

      req.startAsync().dispatch(target);
    }
  }

  @WebServlet(asyncSupported = true)
  public static class DispatchAsync extends HttpServlet {
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) {
      String target = req.getServletPath().replace("/dispatch", "");
      if (req.getQueryString() != null) {
        target += "?" + req.getQueryString();
      }

      AsyncContext context = req.startAsync();
      context.start(
          () -> {
            context.dispatch(target);
          });
    }
  }

  @WebServlet(asyncSupported = true)
  public static class DispatchRecursive extends HttpServlet {
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) {
      if (req.getServletPath().equals("/recursive")) {
        try {
          resp.getWriter().print("Hello Recursive");
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }

      int depth = Integer.parseInt(req.getParameter("depth"));
      if (depth > 0) {
        req.startAsync().dispatch("/dispatch/recursive?depth=" + (depth - 1));
      } else {
        req.startAsync().dispatch("/recursive");
      }
    }
  }
}
