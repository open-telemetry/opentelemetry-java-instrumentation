/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webmvc.v6_0;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesGetter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import javax.annotation.Nullable;

enum SpringWebMvcNetAttributesGetter
    implements NetServerAttributesGetter<HttpServletRequest, HttpServletResponse> {
  INSTANCE;

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
  public String getClientSocketAddress(
      HttpServletRequest request, @Nullable HttpServletResponse response) {
    return request.getRemoteAddr();
  }

  @Override
  public Integer getClientSocketPort(
      HttpServletRequest request, @Nullable HttpServletResponse response) {
    return request.getRemotePort();
  }

  @Nullable
  @Override
  public String getServerSocketAddress(
      HttpServletRequest request, @Nullable HttpServletResponse response) {
    return request.getLocalAddr();
  }

  @Override
  public Integer getServerSocketPort(
      HttpServletRequest request, @Nullable HttpServletResponse response) {
    return request.getLocalPort();
  }
}
