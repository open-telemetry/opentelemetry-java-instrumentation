/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test.filter

import io.opentelemetry.instrumentation.test.base.HttpServerTest
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

import javax.servlet.Filter
import javax.servlet.FilterChain
import javax.servlet.FilterConfig
import javax.servlet.ServletException
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.PATH_PARAM
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS

@Configuration
class ServletFilterConfig {

  @Bean
  Filter servletFilter() {
    return new Filter() {

      @Override
      void init(FilterConfig filterConfig) throws ServletException {
      }

      @Override
      void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request
        HttpServletResponse resp = (HttpServletResponse) response
        ServerEndpoint endpoint = ServerEndpoint.forPath(req.servletPath)
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
            case PATH_PARAM:
              resp.status = endpoint.status
              resp.writer.print(endpoint.body)
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
              resp.sendError(endpoint.status, endpoint.body)
              break
            case EXCEPTION:
              throw new Exception(endpoint.body)
            case INDEXED_CHILD:
              INDEXED_CHILD.collectSpanAttributes { name -> req.getParameter(name) }
              resp.writer.print(endpoint.body)
              break
            default:
              chain.doFilter(request, response)
          }
        }
      }

      @Override
      void destroy() {
      }
    }
  }
}
