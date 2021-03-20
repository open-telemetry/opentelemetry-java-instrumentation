/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet;

import java.security.Principal;

public interface ServletAccessor<RequestT, ResponseT> {
  String getRequestContextPath(RequestT request);

  String getRequestScheme(RequestT request);

  String getRequestServerName(RequestT request);

  int getRequestServerPort(RequestT request);

  String getRequestUri(RequestT request);

  String getRequestQueryString(RequestT request);

  Object getRequestAttribute(RequestT request, String name);

  void setRequestAttribute(RequestT request, String name, Object value);

  String getRequestProtocol(RequestT request);

  String getRequestMethod(RequestT request);

  String getRequestRemoteAddr(RequestT request);

  String getRequestHeader(RequestT request, String name);

  String getRequestServletPath(RequestT request);

  Principal getRequestUserPrincipal(RequestT request);

  Integer getRequestRemotePort(RequestT request);

  int getResponseStatus(ResponseT response);

  boolean isResponseCommitted(ResponseT response);

  boolean isServletException(Throwable throwable);
}
