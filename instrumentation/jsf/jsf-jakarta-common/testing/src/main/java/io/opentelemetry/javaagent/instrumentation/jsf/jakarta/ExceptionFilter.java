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
    } catch (ServletException e) {
      // to ease testing unwrap our exception to root cause
      Throwable tmp = e;
      while (tmp.getCause() != null) {
        tmp = tmp.getCause();
      }
      if (tmp instanceof IllegalStateException && "submit exception".equals(tmp.getMessage())) {
        throw (IllegalStateException) tmp;
      }
      throw e;
    }
  }

  @Override
  public void destroy() {}
}
