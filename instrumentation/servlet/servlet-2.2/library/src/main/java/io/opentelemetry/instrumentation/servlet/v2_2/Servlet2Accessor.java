/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v2_2;

import io.opentelemetry.instrumentation.servlet.ServletAsyncListener;
import io.opentelemetry.instrumentation.servlet.javax.JavaxServletAccessor;
import javax.servlet.http.HttpServletRequest;

public class Servlet2Accessor extends JavaxServletAccessor<ResponseWithStatus> {
  public static final Servlet2Accessor INSTANCE = new Servlet2Accessor();

  private Servlet2Accessor() {}

  @Override
  public Integer getRequestRemotePort(HttpServletRequest httpServletRequest) {
    return null;
  }

  @Override
  public void addRequestAsyncListener(
      HttpServletRequest request,
      ServletAsyncListener<ResponseWithStatus> listener,
      Object response) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getResponseStatus(ResponseWithStatus responseWithStatus) {
    return responseWithStatus.getStatus();
  }

  @Override
  public boolean isResponseCommitted(ResponseWithStatus responseWithStatus) {
    return responseWithStatus.getResponse().isCommitted();
  }
}
