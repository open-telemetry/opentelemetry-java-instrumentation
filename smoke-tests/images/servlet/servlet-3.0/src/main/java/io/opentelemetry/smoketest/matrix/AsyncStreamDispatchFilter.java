/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.matrix;

import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

public class AsyncStreamDispatchFilter implements Filter {
  static final String WRAPPER_CLASS_ATTRIBUTE =
      AsyncStreamDispatchFilter.class.getName() + ".class";

  @Override
  public void init(FilterConfig filterConfig) {}

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    response.setContentType("text/html;charset=UTF-8");
    response.getOutputStream();
    request.setAttribute(WRAPPER_CLASS_ATTRIBUTE, response.getClass());
    request.startAsync().dispatch("/async-stream-snippet/reacquire");
  }

  @Override
  public void destroy() {}
}
