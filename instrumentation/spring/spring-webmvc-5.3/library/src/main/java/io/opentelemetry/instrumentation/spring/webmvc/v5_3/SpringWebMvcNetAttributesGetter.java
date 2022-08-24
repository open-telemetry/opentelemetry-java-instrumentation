/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webmvc.v5_3;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;

enum SpringWebMvcNetAttributesGetter implements NetServerAttributesGetter<HttpServletRequest> {
  INSTANCE;

  @Override
  public String transport(HttpServletRequest request) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Nullable
  @Override
  public String hostName(HttpServletRequest request) {
    return request.getServerName();
  }

  @Override
  public Integer hostPort(HttpServletRequest request) {
    return request.getServerPort();
  }

  @Nullable
  @Override
  public String sockFamily(HttpServletRequest request) {
    return null;
  }

  @Override
  @Nullable
  public String sockPeerAddr(HttpServletRequest request) {
    return request.getRemoteAddr();
  }

  @Override
  public Integer sockPeerPort(HttpServletRequest request) {
    return request.getRemotePort();
  }

  @Nullable
  @Override
  public String sockHostAddr(HttpServletRequest request) {
    return request.getLocalAddr();
  }

  @Nullable
  @Override
  public String sockHostName(HttpServletRequest request) {
    return request.getLocalName();
  }

  @Override
  public Integer sockHostPort(HttpServletRequest request) {
    return request.getLocalPort();
  }
}
