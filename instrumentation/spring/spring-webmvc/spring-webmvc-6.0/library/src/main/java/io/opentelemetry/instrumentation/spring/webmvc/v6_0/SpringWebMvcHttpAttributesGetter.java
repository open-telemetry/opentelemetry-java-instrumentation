/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webmvc.v6_0;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesGetter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import javax.annotation.Nullable;

enum SpringWebMvcHttpAttributesGetter
    implements HttpServerAttributesGetter<HttpServletRequest, HttpServletResponse> {
  INSTANCE;

  @Override
  @Nullable
  public String getHttpRequestMethod(HttpServletRequest request) {
    return request.getMethod();
  }

  @Override
  public List<String> getHttpRequestHeader(HttpServletRequest request, String name) {
    Enumeration<String> headers = request.getHeaders(name);
    return headers == null ? Collections.emptyList() : Collections.list(headers);
  }

  @Override
  public Integer getHttpResponseStatusCode(
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
  public List<String> getHttpResponseHeader(
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
  public String getUrlScheme(HttpServletRequest request) {
    return request.getScheme();
  }

  @Nullable
  @Override
  public String getUrlPath(HttpServletRequest request) {
    return request.getRequestURI();
  }

  @Nullable
  @Override
  public String getUrlQuery(HttpServletRequest request) {
    return request.getQueryString();
  }

  @Nullable
  @Override
  public String getNetworkProtocolName(
      HttpServletRequest request, @Nullable HttpServletResponse response) {
    String protocol = request.getProtocol();
    if (protocol != null && protocol.startsWith("HTTP/")) {
      return "http";
    }
    return null;
  }

  @Nullable
  @Override
  public String getNetworkProtocolVersion(
      HttpServletRequest request, @Nullable HttpServletResponse response) {
    String protocol = request.getProtocol();
    if (protocol != null && protocol.startsWith("HTTP/")) {
      return protocol.substring("HTTP/".length());
    }
    return null;
  }

  @Nullable
  @Override
  public String getServerAddress(HttpServletRequest request) {
    return request.getServerName();
  }

  @Override
  public Integer getServerPort(HttpServletRequest request) {
    return request.getServerPort();
  }

  @Override
  @Nullable
  public String getNetworkPeerAddress(
      HttpServletRequest request, @Nullable HttpServletResponse response) {
    return request.getRemoteAddr();
  }

  @Override
  public Integer getNetworkPeerPort(
      HttpServletRequest request, @Nullable HttpServletResponse response) {
    return request.getRemotePort();
  }

  @Nullable
  @Override
  public String getNetworkLocalAddress(
      HttpServletRequest request, @Nullable HttpServletResponse response) {
    return request.getLocalAddr();
  }

  @Override
  public Integer getNetworkLocalPort(
      HttpServletRequest request, @Nullable HttpServletResponse response) {
    return request.getLocalPort();
  }
}
