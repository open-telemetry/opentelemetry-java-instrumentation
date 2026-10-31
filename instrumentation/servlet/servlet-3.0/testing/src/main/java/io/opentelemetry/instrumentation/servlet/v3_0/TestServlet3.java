/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v3_0;

import static io.opentelemetry.instrumentation.servlet.v3_0.AbstractServlet3Test.HTML_PRINT_WRITER;
import static io.opentelemetry.instrumentation.servlet.v3_0.AbstractServlet3Test.HTML_SERVLET_OUTPUT_STREAM;
import static io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest.controller;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_PARAMETERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;
import static java.nio.charset.StandardCharsets.UTF_8;

import io.opentelemetry.instrumentation.testing.GlobalTraceUtil;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.CountDownLatch;
import javax.servlet.AsyncContext;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class TestServlet3 {

  private TestServlet3() {}

  @WebServlet
  public static class Sync extends HttpServlet {
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
      String servletPath = (String) req.getAttribute(RequestDispatcher.INCLUDE_SERVLET_PATH);
      if (servletPath == null) {
        servletPath = req.getServletPath();
      }

      ServerEndpoint endpoint = ServerEndpoint.forPath(servletPath);
      controller(
          endpoint,
          () -> {
            resp.setContentType("text/plain");
            if (SUCCESS.equals(endpoint)) {
              resp.setStatus(endpoint.getStatus());
              resp.getWriter().print(endpoint.getBody());
            } else if (INDEXED_CHILD.equals(endpoint)) {
              endpoint.collectSpanAttributes(req::getParameter);
              resp.setStatus(endpoint.getStatus());
              resp.getWriter().print(endpoint.getBody());
            } else if (QUERY_PARAM.equals(endpoint)) {
              resp.setStatus(endpoint.getStatus());
              resp.getWriter().print(req.getQueryString());
            } else if (REDIRECT.equals(endpoint)) {
              resp.sendRedirect(endpoint.getBody());
            } else if (CAPTURE_HEADERS.equals(endpoint)) {
              resp.setHeader("X-Test-Response", req.getHeader("X-Test-Request"));
              resp.setStatus(endpoint.getStatus());
              resp.getWriter().print(endpoint.getBody());
            } else if (CAPTURE_PARAMETERS.equals(endpoint)) {
              req.setCharacterEncoding("UTF8");
              String value = req.getParameter("test-parameter");
              if (!"test value õäöü".equals(value)) {
                throw new IllegalStateException(
                    "request parameter does not have expected value " + value);
              }

              resp.setStatus(endpoint.getStatus());
              resp.getWriter().print(endpoint.getBody());
            } else if (ERROR.equals(endpoint)) {
              resp.sendError(endpoint.getStatus(), endpoint.getBody());
            } else if (EXCEPTION.equals(endpoint)) {
              throw new IllegalStateException(endpoint.getBody());
            } else if (HTML_PRINT_WRITER.equals(endpoint)) {
              resp.setStatus(endpoint.getStatus());
              if ("true".equals(req.getParameter("lateContentType"))) {
                String body = "<head>late content type</head>";
                PrintWriter writer = resp.getWriter();
                resp.setContentLength(body.length());
                resp.setContentType("text/html");
                writer.write("<he");
                writer.write("ad>late content type</head>");
              } else {
                resp.setContentType("text/html");
                resp.setContentLength(endpoint.getBody().length());
                resp.getWriter().print(endpoint.getBody());
              }
            } else if (HTML_SERVLET_OUTPUT_STREAM.equals(endpoint)) {
              resp.setStatus(endpoint.getStatus());
              if ("true".equals(req.getParameter("lateContentType"))) {
                String body = "<head>late content type</head>";
                ServletOutputStream output = resp.getOutputStream();
                resp.setContentLength(body.length());
                resp.setContentType("text/html");
                output.write("<he".getBytes(UTF_8));
                output.write("ad>late content type</head>".getBytes(UTF_8));
              } else {
                resp.setContentType("text/html");
                resp.setContentLength(endpoint.getBody().length());
                byte[] body = endpoint.getBody().getBytes(UTF_8);
                resp.getOutputStream().write(body, 0, body.length);
              }
            }
            return null;
          });
    }
  }

  @WebServlet(asyncSupported = true)
  public static class SnippetForward extends HttpServlet {
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {
      resp.setContentType("text/html");
      String contentLength = req.getParameter("contentLength");
      resp.setContentLength(contentLength == null ? 1000 : Integer.parseInt(contentLength));
      resp.getOutputStream().write("<head>".getBytes(UTF_8));
      if (resp.isCommitted()) {
        throw new ServletException("Response committed before forward");
      }
      req.getRequestDispatcher(HTML_PRINT_WRITER.getPath()).forward(req, resp);
    }
  }

  @WebServlet(asyncSupported = true)
  public static class SnippetAsyncReset extends HttpServlet {
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
      resp.setContentType("text/html");
      resp.getWriter().write("<head>");
      AsyncContext context = req.startAsync();
      context.getResponse().resetBuffer();
      context.dispatch(HTML_PRINT_WRITER.getPath());
    }
  }

  @WebServlet
  public static class SnippetRejectedForward extends HttpServlet {
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {
      resp.setContentType("text/html");
      ServletOutputStream output = resp.getOutputStream();
      output.write("<head><title>before</title></head>".getBytes(UTF_8));
      resp.flushBuffer();
      try {
        req.getRequestDispatcher(HTML_PRINT_WRITER.getPath()).forward(req, resp);
      } catch (IllegalStateException expected) {
        output.write("<head><title>after</title></head>".getBytes(UTF_8));
        return;
      }
      throw new ServletException("Forward unexpectedly succeeded");
    }
  }

  @WebServlet
  public static class SnippetWriterAcquisitionFailure extends HttpServlet {
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {
      String body = HTML_PRINT_WRITER.getBody();
      resp.setContentType("text/html");
      resp.setContentLength(body.length());
      ServletOutputStream output = resp.getOutputStream();
      try {
        resp.getWriter();
      } catch (IllegalStateException expected) {
        output.write(body.getBytes(UTF_8));
        return;
      }
      throw new ServletException("Writer acquisition unexpectedly succeeded");
    }
  }

  @WebServlet(asyncSupported = true)
  public static class Async extends HttpServlet {
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) {
      ServerEndpoint endpoint = ServerEndpoint.forPath(req.getServletPath());
      CountDownLatch latch = new CountDownLatch(1);
      boolean startAsyncInSpan =
          SUCCESS.equals(endpoint) && "true".equals(req.getParameter("startAsyncInSpan"));
      AsyncContext context =
          startAsyncInSpan
              ? GlobalTraceUtil.runWithSpan("startAsync", () -> req.startAsync())
              : req.startAsync();
      if (endpoint.equals(EXCEPTION)) {
        context.setTimeout(5000);
      }

      context.start(
          () -> {
            try {
              controller(
                  endpoint,
                  () -> {
                    resp.setContentType("text/plain");
                    if (SUCCESS.equals(endpoint)) {
                      resp.setStatus(endpoint.getStatus());
                      resp.getWriter().print(endpoint.getBody());
                      context.complete();
                    } else if (INDEXED_CHILD.equals(endpoint)) {
                      endpoint.collectSpanAttributes(req::getParameter);
                      resp.setStatus(endpoint.getStatus());
                      resp.getWriter().print(endpoint.getBody());
                      context.complete();
                    } else if (QUERY_PARAM.equals(endpoint)) {
                      resp.setStatus(endpoint.getStatus());
                      resp.getWriter().print(req.getQueryString());
                      context.complete();
                    } else if (REDIRECT.equals(endpoint)) {
                      resp.sendRedirect(endpoint.getBody());
                      context.complete();
                    } else if (CAPTURE_HEADERS.equals(endpoint)) {
                      resp.setHeader("X-Test-Response", req.getHeader("X-Test-Request"));
                      resp.setStatus(endpoint.getStatus());
                      resp.getWriter().print(endpoint.getBody());
                      context.complete();
                    } else if (CAPTURE_PARAMETERS.equals(endpoint)) {
                      req.setCharacterEncoding("UTF8");
                      String value = req.getParameter("test-parameter");
                      if (!"test value õäöü".equals(value)) {
                        throw new IllegalStateException(
                            "request parameter does not have expected value " + value);
                      }

                      resp.setStatus(endpoint.getStatus());
                      resp.getWriter().print(endpoint.getBody());
                      context.complete();
                    } else if (ERROR.equals(endpoint)) {
                      resp.setStatus(endpoint.getStatus());
                      resp.getWriter().print(endpoint.getBody());
                      context.complete();
                    } else if (EXCEPTION.equals(endpoint)) {
                      resp.setStatus(endpoint.getStatus());
                      PrintWriter writer = resp.getWriter();
                      writer.print(endpoint.getBody());
                      if (req.getServletContext().getClass().getName().contains("catalina")) {
                        // on tomcat close the writer to ensure response is sent immediately,
                        // otherwise there is a chance that tomcat resets the connection before the
                        // response is sent
                        writer.close();
                        resp.flushBuffer();
                      }
                      throw new IllegalStateException(endpoint.getBody());
                    } else if (HTML_PRINT_WRITER.equals(endpoint)) {
                      resp.setContentType("text/html");
                      resp.setStatus(endpoint.getStatus());
                      resp.setContentLength(endpoint.getBody().length());
                      resp.getWriter().print(endpoint.getBody());
                      context.complete();
                    } else if (HTML_SERVLET_OUTPUT_STREAM.equals(endpoint)) {
                      resp.setContentType("text/html");
                      resp.setStatus(endpoint.getStatus());
                      resp.getOutputStream().print(endpoint.getBody());
                      context.complete();
                    }
                    return null;
                  });
            } catch (Exception e) {
              if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
              }
              throw new IllegalStateException(e);
            } finally {
              latch.countDown();
            }
          });
      try {
        latch.await();
      } catch (InterruptedException ignored) {
        Thread.currentThread().interrupt();
      }
    }
  }

  @WebServlet(asyncSupported = true)
  public static class FakeAsync extends HttpServlet {
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
      AsyncContext context = req.startAsync();
      try {
        ServerEndpoint endpoint = ServerEndpoint.forPath(req.getServletPath());

        controller(
            endpoint,
            () -> {
              resp.setContentType("text/plain");
              if (SUCCESS.equals(endpoint)) {
                resp.setStatus(endpoint.getStatus());
                resp.getWriter().print(endpoint.getBody());
              } else if (INDEXED_CHILD.equals(endpoint)) {
                endpoint.collectSpanAttributes(req::getParameter);
                resp.setStatus(endpoint.getStatus());
                resp.getWriter().print(endpoint.getBody());
              } else if (QUERY_PARAM.equals(endpoint)) {
                resp.setStatus(endpoint.getStatus());
                resp.getWriter().print(req.getQueryString());
              } else if (REDIRECT.equals(endpoint)) {
                resp.sendRedirect(endpoint.getBody());
              } else if (CAPTURE_HEADERS.equals(endpoint)) {
                resp.setHeader("X-Test-Response", req.getHeader("X-Test-Request"));
                resp.setStatus(endpoint.getStatus());
                resp.getWriter().print(endpoint.getBody());
              } else if (CAPTURE_PARAMETERS.equals(endpoint)) {
                req.setCharacterEncoding("UTF8");
                String value = req.getParameter("test-parameter");
                if (!"test value õäöü".equals(value)) {
                  throw new IllegalStateException(
                      "request parameter does not have expected value " + value);
                }

                resp.setStatus(endpoint.getStatus());
                resp.getWriter().print(endpoint.getBody());
              } else if (ERROR.equals(endpoint)) {
                resp.sendError(endpoint.getStatus(), endpoint.getBody());
              } else if (EXCEPTION.equals(endpoint)) {
                resp.setStatus(endpoint.getStatus());
                resp.getWriter().print(endpoint.getBody());
                throw new IllegalStateException(endpoint.getBody());
              } else if (HTML_PRINT_WRITER.equals(endpoint)) {
                // intentionally testing setting status before contentType here to cover that case
                // somewhere
                resp.setStatus(endpoint.getStatus());
                resp.setContentType("text/html");
                resp.getWriter().print(endpoint.getBody());
              } else if (HTML_SERVLET_OUTPUT_STREAM.equals(endpoint)) {
                resp.setContentType("text/html");
                resp.setStatus(endpoint.getStatus());
                resp.getOutputStream().print(endpoint.getBody());
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
      AsyncContext context = req.startAsync();
      context.start(
          () -> {
            String target = req.getServletPath().replace("/dispatch", "");
            if (req.getQueryString() != null) {
              target += "?" + req.getQueryString();
            }
            context.dispatch(target);
          });
    }
  }

  @WebServlet(asyncSupported = true)
  public static class DispatchRecursive extends HttpServlet {
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
      if (req.getServletPath().equals("/recursive")) {
        resp.getWriter().print("Hello Recursive");
        return;
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
