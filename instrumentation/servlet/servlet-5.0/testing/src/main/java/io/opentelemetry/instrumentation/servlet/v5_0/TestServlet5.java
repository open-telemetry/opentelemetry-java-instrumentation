/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v5_0;

import static io.opentelemetry.instrumentation.servlet.v5_0.AbstractServlet5Test.HTML_PRINT_WRITER;
import static io.opentelemetry.instrumentation.servlet.v5_0.AbstractServlet5Test.HTML_SERVLET_OUTPUT_STREAM;
import static io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest.controller;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_BODY;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_BODY_DISPATCH_READ;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_BODY_LATE_READ;
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
import jakarta.servlet.AsyncContext;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.CountDownLatch;

public class TestServlet5 {

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
            } else if (isCaptureBody(endpoint)) {
              // read body to trigger body capture
              consumeRequestBody(req);
              resp.setStatus(endpoint.getStatus());
              resp.getWriter().print(endpoint.getBody());
            } else if (ERROR.equals(endpoint)) {
              resp.sendError(endpoint.getStatus(), endpoint.getBody());
            } else if (EXCEPTION.equals(endpoint)) {
              throw new IllegalStateException(endpoint.getBody());
            } else if (HTML_PRINT_WRITER.equals(endpoint)) {
              resp.setContentType("text/html");
              resp.setStatus(endpoint.getStatus());
              resp.setContentLength(endpoint.getBody().length());
              resp.getWriter().print(endpoint.getBody());
            } else if (HTML_SERVLET_OUTPUT_STREAM.equals(endpoint)) {
              resp.setContentType("text/html");
              resp.setStatus(endpoint.getStatus());
              resp.setContentLength(endpoint.getBody().length());
              byte[] body = endpoint.getBody().getBytes(UTF_8);
              resp.getOutputStream().write(body, 0, body.length);
            }
            return null;
          });
    }
  }

  @WebServlet(asyncSupported = true)
  public static class Async extends HttpServlet {
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) {
      ServerEndpoint endpoint = ServerEndpoint.forPath(req.getServletPath());
      if (CAPTURE_BODY_DISPATCH_READ.equals(endpoint)) {
        // the body is read by the dispatched servlet, after this method has returned
        req.startAsync().dispatch(CAPTURE_BODY.getPath());
        return;
      }
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
                    } else if (CAPTURE_BODY.equals(endpoint)
                        || CAPTURE_BODY_LATE_READ.equals(endpoint)) {
                      if (CAPTURE_BODY_LATE_READ.equals(endpoint)) {
                        // In the case of a late read, the request body is read after the servlet
                        // service() method has returned.
                        // This fixed delay is to let the servlet service() method to complete.
                        // We can't use a latch to wait as it would trigger the read within the
                        // servlet service() method.
                        //
                        // In the rare case where the fixed timeout is not enough, this would make
                        // the body capture to happen before the service() method returns.
                        // That would make the test scenario equivalent to the synchronous body
                        // capture, so no test failure is triggered.
                        Thread.sleep(20);
                      }
                      // read body to trigger body capture
                      consumeRequestBody(req);
                      resp.setStatus(endpoint.getStatus());
                      resp.getWriter().print(endpoint.getBody());
                      if (!CAPTURE_BODY_LATE_READ.equals(endpoint)) {
                        context.complete();
                      }
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
              if (CAPTURE_BODY_LATE_READ.equals(endpoint)) {
                // nothing waits for this thread: complete once the controller span has ended
                context.complete();
              }
              latch.countDown();
            }
          });
      if (CAPTURE_BODY_LATE_READ.equals(endpoint)) {
        // return without waiting, the request body is read after returning
        return;
      }
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
              } else if (isCaptureBody(endpoint)) {
                // read body to trigger body capture
                consumeRequestBody(req);
                resp.setStatus(endpoint.getStatus());
                resp.getWriter().print(endpoint.getBody());
              } else if (ERROR.equals(endpoint)) {
                resp.sendError(endpoint.getStatus(), endpoint.getBody());
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

  // servlets that cannot read the body late or after a dispatch just read it directly
  private static boolean isCaptureBody(ServerEndpoint endpoint) {
    return CAPTURE_BODY.equals(endpoint)
        || CAPTURE_BODY_LATE_READ.equals(endpoint)
        || CAPTURE_BODY_DISPATCH_READ.equals(endpoint);
  }

  private static void consumeRequestBody(HttpServletRequest req) throws IOException {
    ServletInputStream requestBody = req.getInputStream();
    while (!requestBody.isFinished()) {
      requestBody.read();
    }
  }

  private TestServlet5() {}
}
