/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webmvc.v6_0;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import jakarta.servlet.http.HttpServletRequest;
import javax.annotation.Nullable;

enum SpringWebMvcNetAttributesGetter implements NetServerAttributesGetter<HttpServletRequest> {
  INSTANCE;

  @Override
  public String getTransport(HttpServletRequest request) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
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
