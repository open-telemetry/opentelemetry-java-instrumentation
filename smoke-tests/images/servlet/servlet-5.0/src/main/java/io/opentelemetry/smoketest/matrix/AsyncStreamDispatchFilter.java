/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.matrix;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;

public class AsyncStreamDispatchFilter implements Filter {
  static final String WRAPPER_CLASS_ATTRIBUTE =
      AsyncStreamDispatchFilter.class.getName() + ".class";

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    response.setContentType("text/html;charset=UTF-8");
    response.getOutputStream();
    request.setAttribute(WRAPPER_CLASS_ATTRIBUTE, response.getClass());
    request.startAsync().dispatch("/async-stream-snippet/reacquire");
  }
}
