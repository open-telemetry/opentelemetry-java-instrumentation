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

  @Override
  public Integer sockPeerPort(HttpServletRequest request) {
    return request.getRemotePort();
  }

  @Override
  @Nullable
  public String sockPeerAddr(HttpServletRequest request) {
    return request.getRemoteAddr();
  }
}
