/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v2_2;

import io.opentelemetry.instrumentation.servlet.internal.ServletAsyncListener;
import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseMutator;
import io.opentelemetry.javaagent.instrumentation.servlet.javax.JavaxServletAccessor;
import java.util.Collections;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class Servlet2Accessor extends JavaxServletAccessor<HttpServletResponse>
    implements HttpServerResponseMutator<HttpServletResponse> {
  public static final Servlet2Accessor INSTANCE = new Servlet2Accessor();

  private Servlet2Accessor() {}

  @Override
  public Integer getRequestRemotePort(HttpServletRequest httpServletRequest) {
    return null;
  }

  @Override
  public String getRequestLocalAddr(HttpServletRequest request) {
    return null;
  }

  @Override
  public Integer getRequestLocalPort(HttpServletRequest request) {
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
  public List<String> getResponseHeaderValues(
      HttpServletResponse httpServletResponse, String name) {
    return Collections.emptyList();
  }

  @Override
  public boolean isResponseCommitted(HttpServletResponse httpServletResponse) {
    return httpServletResponse.isCommitted();
  }

  @Override
  public void appendHeader(HttpServletResponse response, String name, String value) {
    response.addHeader(name, value);
  }
}
