/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jsf.jakarta;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;

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
