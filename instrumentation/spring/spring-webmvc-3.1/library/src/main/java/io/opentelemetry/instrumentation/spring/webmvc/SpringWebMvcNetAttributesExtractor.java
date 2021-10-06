/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webmvc;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.checkerframework.checker.nullness.qual.Nullable;

final class SpringWebMvcNetAttributesExtractor
    extends NetServerAttributesExtractor<HttpServletRequest, HttpServletResponse> {
  @Override
  public String transport(HttpServletRequest request) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  public @Nullable String peerName(HttpServletRequest request) {
    return request.getRemoteHost();
  }

  @Override
  public Integer peerPort(HttpServletRequest request) {
    return request.getRemotePort();
  }

  @Override
  public @Nullable String peerIp(HttpServletRequest request) {
    return request.getRemoteAddr();
  }
}
