/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.FilterConfig
import jakarta.servlet.ServletException
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse

class ExceptionFilter implements Filter {
  @Override
  void init(FilterConfig filterConfig) throws ServletException {
  }

  @Override
  void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
    try {
      chain.doFilter(request, response)
    } catch (Exception exception) {
      // to ease testing unwrap our exception to root cause
      Exception tmp = exception
      while (tmp.getCause() != null) {
        tmp = tmp.getCause()
      }
      if (tmp.getMessage() != null && tmp.getMessage().contains("submit exception")) {
        throw tmp
      }
      throw exception
    }
  }

  @Override
  void destroy() {
  }
}
