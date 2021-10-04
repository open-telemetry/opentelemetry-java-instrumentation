/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webmvc;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.checkerframework.checker.nullness.qual.Nullable;

final class SpringWebMvcNetAttributesExtractor
    extends NetAttributesExtractor<HttpServletRequest, HttpServletResponse> {
  SpringWebMvcNetAttributesExtractor() {
    super(NetPeerAttributeExtraction.ON_START);
  }

  @Override
  public String transport(HttpServletRequest request) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  public @Nullable String peerName(
      HttpServletRequest request, @Nullable HttpServletResponse response) {
    return request.getRemoteHost();
  }

  @Override
  public Integer peerPort(HttpServletRequest request, @Nullable HttpServletResponse response) {
    return request.getRemotePort();
  }

  @Override
  public @Nullable String peerIp(
      HttpServletRequest request, @Nullable HttpServletResponse response) {
    return request.getRemoteAddr();
  }
}
