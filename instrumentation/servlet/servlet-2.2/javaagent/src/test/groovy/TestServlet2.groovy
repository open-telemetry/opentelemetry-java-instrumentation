/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import groovy.servlet.AbstractHttpServlet
import io.opentelemetry.instrumentation.test.base.HttpServerTest

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.ERROR
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.SUCCESS

class TestServlet2 {

  static class Sync extends AbstractHttpServlet {
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) {
      req.getRequestDispatcher()
      HttpServerTest.ServerEndpoint endpoint = HttpServerTest.ServerEndpoint.forPath(req.servletPath)
      HttpServerTest.controller(endpoint) {
        resp.contentType = "text/plain"
        switch (endpoint) {
          case SUCCESS:
            resp.status = endpoint.status
            resp.writer.print(endpoint.body)
            break
          case QUERY_PARAM:
            resp.status = endpoint.status
            resp.writer.print(req.queryString)
            break
          case REDIRECT:
            resp.sendRedirect(endpoint.body)
            break
          case ERROR:
            resp.sendError(endpoint.status, endpoint.body)
            break
          case EXCEPTION:
            throw new Exception(endpoint.body)
        }
      }
    }
  }
}
