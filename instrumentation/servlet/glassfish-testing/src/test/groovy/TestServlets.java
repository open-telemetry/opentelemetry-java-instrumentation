/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
    protected void service(final HttpServletRequest req, final HttpServletResponse resp) {
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
    protected void service(final HttpServletRequest req, final HttpServletResponse resp) {
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
    protected void service(final HttpServletRequest req, final HttpServletResponse resp) {
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
    protected void service(final HttpServletRequest req, final HttpServletResponse resp) {
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
