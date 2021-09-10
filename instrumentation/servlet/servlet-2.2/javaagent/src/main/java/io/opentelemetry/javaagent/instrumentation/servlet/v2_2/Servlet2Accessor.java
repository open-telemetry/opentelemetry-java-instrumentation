/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v2_2;

import io.opentelemetry.instrumentation.servlet.ServletAsyncListener;
import io.opentelemetry.instrumentation.servlet.javax.JavaxServletAccessor;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class Servlet2Accessor extends JavaxServletAccessor<HttpServletResponse> {
  public static final Servlet2Accessor INSTANCE = new Servlet2Accessor();

  private Servlet2Accessor() {}

  @Override
  public Integer getRequestRemotePort(HttpServletRequest httpServletRequest) {
    return null;
  }

  @Override
  public void addRequestAsyncListener(
      HttpServletRequest httpServletRequest,
      ServletAsyncListener<HttpServletResponse> listener,
      Object response) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getResponseStatus(HttpServletResponse httpServletResponse) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getResponseHeader(HttpServletResponse httpServletResponse, String name) {
    return null;
  }

  @Override
  public boolean isResponseCommitted(HttpServletResponse httpServletResponse) {
    return httpServletResponse.isCommitted();
  }
}
