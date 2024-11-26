/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jsf.javax;

import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

public class ExceptionFilter implements Filter {
  @Override
  public void init(FilterConfig filterConfig) {}

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    try {
      chain.doFilter(request, response);
    } catch (ServletException exception) {
      // to ease testing unwrap our exception to root cause
      Throwable tmp = exception;
      while (tmp.getCause() != null) {
        tmp = tmp.getCause();
      }
      if (tmp.getMessage() != null && tmp.getMessage().contains("submit exception")) {
        throw (IllegalStateException) tmp;
      }
      throw exception;
    }
  }

  @Override
  public void destroy() {}
}
