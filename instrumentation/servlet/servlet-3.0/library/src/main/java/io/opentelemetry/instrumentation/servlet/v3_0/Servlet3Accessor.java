/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v3_0;

import io.opentelemetry.instrumentation.servlet.javax.JavaxServletAccessor;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class Servlet3Accessor extends JavaxServletAccessor<HttpServletResponse> {
  public static final Servlet3Accessor INSTANCE = new Servlet3Accessor();

  private Servlet3Accessor() {}

  @Override
  public Integer getRequestRemotePort(HttpServletRequest request) {
    return request.getRemotePort();
  }

  @Override
  public int getResponseStatus(HttpServletResponse response) {
    return response.getStatus();
  }

  @Override
  public boolean isResponseCommitted(HttpServletResponse response) {
    return response.isCommitted();
  }
}
