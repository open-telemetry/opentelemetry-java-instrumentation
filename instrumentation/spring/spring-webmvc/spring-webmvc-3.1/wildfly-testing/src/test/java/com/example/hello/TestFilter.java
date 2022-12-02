/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.hello;

import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component("testFilter")
public class TestFilter implements Filter {
  public TestFilter() {}

  @Override
  public void init(FilterConfig filterConfig) {}

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest httpServletRequest = (HttpServletRequest) request;
    // To test OpenTelemetryHandlerMappingFilter we need to stop the request before it reaches
    // HandlerAdapter which gives server span the same name as OpenTelemetryHandlerMappingFilter.
    // Throwing an exception from servlet filter works for that.
    if (httpServletRequest.getRequestURI().contains("exception")) {
      throw new ServletException("exception");
    }

    chain.doFilter(request, response);
  }

  @Override
  public void destroy() {}
}
