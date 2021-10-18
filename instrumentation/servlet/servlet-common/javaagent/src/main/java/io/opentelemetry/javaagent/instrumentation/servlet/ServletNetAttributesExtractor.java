/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesExtractor;
import io.opentelemetry.instrumentation.servlet.ServletAccessor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;

public class ServletNetAttributesExtractor<REQUEST, RESPONSE>
    extends NetServerAttributesExtractor<
        ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>> {
  private final ServletAccessor<REQUEST, RESPONSE> accessor;

  public ServletNetAttributesExtractor(ServletAccessor<REQUEST, RESPONSE> accessor) {
    this.accessor = accessor;
  }

  @Override
  @Nullable
  public String transport(ServletRequestContext<REQUEST> requestContext) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  @Nullable
  public String peerName(ServletRequestContext<REQUEST> requestContext) {
    return accessor.getRequestRemoteHost(requestContext.request());
  }

  @Override
  @Nullable
  public Integer peerPort(ServletRequestContext<REQUEST> requestContext) {
    return accessor.getRequestRemotePort(requestContext.request());
  }

  @Override
  @Nullable
  public String peerIp(ServletRequestContext<REQUEST> requestContext) {
    return accessor.getRequestRemoteAddr(requestContext.request());
  }
}
