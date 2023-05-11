/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webmvc.v5_3;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesGetter;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;

enum SpringWebMvcNetAttributesGetter implements NetServerAttributesGetter<HttpServletRequest> {
  INSTANCE;

  @Nullable
  @Override
  public String getProtocolName(HttpServletRequest request) {
    String protocol = request.getProtocol();
    if (protocol != null && protocol.startsWith("HTTP/")) {
      return "http";
    }
    return null;
  }

  @Nullable
  @Override
  public String getProtocolVersion(HttpServletRequest request) {
    String protocol = request.getProtocol();
    if (protocol != null && protocol.startsWith("HTTP/")) {
      return protocol.substring("HTTP/".length());
    }
    return null;
  }

  @Nullable
  @Override
  public String getHostName(HttpServletRequest request) {
    return request.getServerName();
  }

  @Override
  public Integer getHostPort(HttpServletRequest request) {
    return request.getServerPort();
  }

  @Override
  @Nullable
  public String getSockPeerAddr(HttpServletRequest request) {
    return request.getRemoteAddr();
  }

  @Override
  public Integer getSockPeerPort(HttpServletRequest request) {
    return request.getRemotePort();
  }

  @Nullable
  @Override
  public String getSockHostAddr(HttpServletRequest request) {
    return request.getLocalAddr();
  }

  @Override
  public Integer getSockHostPort(HttpServletRequest request) {
    return request.getLocalPort();
  }
}
