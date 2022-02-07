/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webmvc;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;

final class SpringWebMvcNetAttributesGetter
    implements NetServerAttributesGetter<HttpServletRequest> {
  @Override
  public String transport(HttpServletRequest request) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  @Nullable
  public String peerName(HttpServletRequest request) {
    return request.getRemoteHost();
  }

  @Override
  public Integer peerPort(HttpServletRequest request) {
    return request.getRemotePort();
  }

  @Override
  @Nullable
  public String peerIp(HttpServletRequest request) {
    return request.getRemoteAddr();
  }
}
