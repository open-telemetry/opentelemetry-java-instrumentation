/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webmvc.v6_0.filter;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD;

import io.opentelemetry.instrumentation.test.base.HttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ServletFilterConfig {

  @Bean
  Filter servletFilter() {
    return new Filter() {

      @Override
      public void init(FilterConfig filterConfig) throws ServletException {}

      @Override
      public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
          throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
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
                case "QUERY_PARAM":
                  resp.setStatus(endpoint.getStatus());
                  resp.getWriter().print(req.getQueryString());
                  break;
                case "PATH_PARAM":
                  resp.setStatus(endpoint.getStatus());
                  resp.getWriter().print(endpoint.getBody());
                  break;
                case "REDIRECT":
                  resp.sendRedirect(endpoint.getBody());
                  break;
                case "CAPTURE_HEADERS":
                  resp.setHeader("X-Test-Response", req.getHeader("X-Test-Request"));
                  resp.setStatus(endpoint.getStatus());
                  resp.getWriter().print(endpoint.getBody());
                  break;
                case "ERROR":
                  resp.sendError(endpoint.getStatus(), endpoint.getBody());
                  break;
                case "EXCEPTION":
                  throw new Exception(endpoint.getBody());
                case "INDEXED_CHILD":
                  INDEXED_CHILD.collectSpanAttributes(req::getParameter);
                  resp.getWriter().print(endpoint.getBody());
                  break;
                default:
                  chain.doFilter(request, response);
              }
              return null;
            });
      }

      @Override
      public void destroy() {}
    };
  }
}
