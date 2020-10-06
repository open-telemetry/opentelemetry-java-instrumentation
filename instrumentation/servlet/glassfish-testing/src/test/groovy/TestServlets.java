/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import groovy.lang.Closure;
import io.opentelemetry.auto.test.base.HttpServerTest;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class TestServlets {

  @WebServlet("/success")
  public static class Success extends HttpServlet {
    @Override
    protected void service(HttpServletRequest req, final HttpServletResponse resp) {
      final HttpServerTest.ServerEndpoint endpoint =
          HttpServerTest.ServerEndpoint.forPath(req.getServletPath());
      HttpServerTest.controller(
          endpoint,
          new Closure(null) {
            public Object doCall() throws Exception {
              resp.setContentType("text/plain");
              resp.setStatus(endpoint.getStatus());
              resp.getWriter().print(endpoint.getBody());
              return null;
            }
          });
    }
  }

  @WebServlet("/query")
  public static class Query extends HttpServlet {
    @Override
    protected void service(final HttpServletRequest req, final HttpServletResponse resp) {
      final HttpServerTest.ServerEndpoint endpoint =
          HttpServerTest.ServerEndpoint.forPath(req.getServletPath());
      HttpServerTest.controller(
          endpoint,
          new Closure(null) {
            public Object doCall() throws Exception {
              resp.setContentType("text/plain");
              resp.setStatus(endpoint.getStatus());
              resp.getWriter().print(req.getQueryString());
              return null;
            }
          });
    }
  }

  @WebServlet("/redirect")
  public static class Redirect extends HttpServlet {
    @Override
    protected void service(HttpServletRequest req, final HttpServletResponse resp) {
      final HttpServerTest.ServerEndpoint endpoint =
          HttpServerTest.ServerEndpoint.forPath(req.getServletPath());
      HttpServerTest.controller(
          endpoint,
          new Closure(null) {
            public Object doCall() throws Exception {
              resp.sendRedirect(endpoint.getBody());
              return null;
            }
          });
    }
  }

  @WebServlet("/error-status")
  public static class Error extends HttpServlet {
    @Override
    protected void service(HttpServletRequest req, final HttpServletResponse resp) {
      final HttpServerTest.ServerEndpoint endpoint =
          HttpServerTest.ServerEndpoint.forPath(req.getServletPath());
      HttpServerTest.controller(
          endpoint,
          new Closure(null) {
            public Object doCall() throws Exception {
              resp.setContentType("text/plain");
              resp.sendError(endpoint.getStatus(), endpoint.getBody());
              return null;
            }
          });
    }
  }

  @WebServlet("/exception")
  public static class ExceptionServlet extends HttpServlet {
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) {
      final HttpServerTest.ServerEndpoint endpoint =
          HttpServerTest.ServerEndpoint.forPath(req.getServletPath());
      HttpServerTest.controller(
          endpoint,
          new Closure(null) {
            public Object doCall() throws Exception {
              throw new Exception(endpoint.getBody());
            }
          });
    }
  }
}
