/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webmvc.v5_3;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesGetter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

enum SpringWebMvcHttpAttributesGetter
    implements HttpServerAttributesGetter<HttpServletRequest, HttpServletResponse> {
  INSTANCE;

  @Override
  @Nullable
  public String getMethod(HttpServletRequest request) {
    return request.getMethod();
  }

  @Override
  public List<String> getRequestHeader(HttpServletRequest request, String name) {
    Enumeration<String> headers = request.getHeaders(name);
    return headers == null ? Collections.emptyList() : Collections.list(headers);
  }

  @Override
  @Nullable
  public String getFlavor(HttpServletRequest request) {
    String flavor = request.getProtocol();
    if (flavor == null) {
      return null;
    }
    if (flavor.startsWith("HTTP/")) {
      flavor = flavor.substring("HTTP/".length());
    }
    return flavor;
  }

  @Override
  public Integer getStatusCode(
      HttpServletRequest request, HttpServletResponse response, @Nullable Throwable error) {

    int statusCode;
    // if response is not committed and there is a throwable set status to 500 /
    // INTERNAL_SERVER_ERROR, due to servlet spec
    // https://javaee.github.io/servlet-spec/downloads/servlet-4.0/servlet-4_0_FINAL.pdf:
    // "If a servlet generates an error that is not handled by the error page mechanism as
    // described above, the container must ensure to send a response with status 500."
    if (!response.isCommitted() && error != null) {
      statusCode = 500;
    } else {
      statusCode = response.getStatus();
    }

    return statusCode;
  }

  @Override
  public List<String> getResponseHeader(
      HttpServletRequest request, HttpServletResponse response, String name) {
    Collection<String> headers = response.getHeaders(name);
    if (headers == null) {
      return Collections.emptyList();
    }
    if (headers instanceof List) {
      return (List<String>) headers;
    }
    return new ArrayList<>(headers);
  }

  @Override
  @Nullable
  public String getTarget(HttpServletRequest request) {
    String target = request.getRequestURI();
    String queryString = request.getQueryString();
    if (queryString != null) {
      target += "?" + queryString;
    }
    return target;
  }

  @Override
  @Nullable
  public String getScheme(HttpServletRequest request) {
    return request.getScheme();
  }
}
