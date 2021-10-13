/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.v7_0

import groovy.servlet.AbstractHttpServlet
import io.opentelemetry.instrumentation.test.base.HttpServerTest

import javax.servlet.annotation.WebServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.CAPTURE_HEADERS
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.ERROR
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.INDEXED_CHILD
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.SUCCESS

@WebServlet(asyncSupported = true)
class AsyncServlet extends AbstractHttpServlet {
  @Override
  protected void service(HttpServletRequest req, HttpServletResponse resp) {
    HttpServerTest.ServerEndpoint endpoint = HttpServerTest.ServerEndpoint.forPath(req.servletPath)
    def context = req.startAsync()
    context.start {
      try {
        HttpServerTest.controller(endpoint) {
          resp.contentType = "text/plain"
          switch (endpoint) {
            case SUCCESS:
              resp.status = endpoint.status
              resp.writer.print(endpoint.body)
              break
            case INDEXED_CHILD:
              endpoint.collectSpanAttributes { req.getParameter(it) }
              resp.status = endpoint.status
              break
            case QUERY_PARAM:
              resp.status = endpoint.status
              resp.writer.print(req.queryString)
              break
            case REDIRECT:
              resp.sendRedirect(endpoint.body)
              break
            case CAPTURE_HEADERS:
              resp.setHeader("X-Test-Response", req.getHeader("X-Test-Request"))
              resp.status = endpoint.status
              resp.writer.print(endpoint.body)
              break
            case ERROR:
              resp.status = endpoint.status
              resp.writer.print(endpoint.body)
              break
            case EXCEPTION:
              resp.status = endpoint.status
              resp.writer.print(endpoint.body)
              throw new Exception(endpoint.body)
          }
        }
      } finally {
        // complete at the end so the server span will end after the controller span
        context.complete()
      }
    }
  }
}
