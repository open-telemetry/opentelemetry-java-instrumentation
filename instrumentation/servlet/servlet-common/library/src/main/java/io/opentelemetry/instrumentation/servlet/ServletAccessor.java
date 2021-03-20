/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet;

import java.security.Principal;

public interface ServletAccessor<REQUEST, RESPONSE> {
  String getRequestContextPath(REQUEST request);

  String getRequestScheme(REQUEST request);

  String getRequestServerName(REQUEST request);

  int getRequestServerPort(REQUEST request);

  String getRequestURI(REQUEST request);

  String getRequestQueryString(REQUEST request);

  Object getRequestAttribute(REQUEST request, String name);

  void setRequestAttribute(REQUEST request, String name, Object value);

  String getRequestProtocol(REQUEST request);

  String getRequestMethod(REQUEST request);

  String getRequestRemoteAddr(REQUEST request);

  String getRequestHeader(REQUEST request, String name);

  String getRequestServletPath(REQUEST request);

  Principal getRequestUserPrincipal(REQUEST request);

  Integer getRequestRemotePort(REQUEST request);

  int getResponseStatus(RESPONSE response);

  boolean isResponseCommitted(RESPONSE response);

  boolean isServletException(Throwable throwable);
}
