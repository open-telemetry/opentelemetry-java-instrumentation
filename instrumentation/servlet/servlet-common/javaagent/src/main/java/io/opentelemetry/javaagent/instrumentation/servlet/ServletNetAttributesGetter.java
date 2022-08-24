/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;

public class ServletNetAttributesGetter<REQUEST, RESPONSE>
    implements NetServerAttributesGetter<ServletRequestContext<REQUEST>> {

  private final ServletAccessor<REQUEST, RESPONSE> accessor;

  public ServletNetAttributesGetter(ServletAccessor<REQUEST, RESPONSE> accessor) {
    this.accessor = accessor;
  }

  @Override
  @Nullable
  public String transport(ServletRequestContext<REQUEST> requestContext) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Nullable
  @Override
  public String hostName(ServletRequestContext<REQUEST> requestContext) {
    return accessor.getRequestServerName(requestContext.request());
  }

  @Nullable
  @Override
  public Integer hostPort(ServletRequestContext<REQUEST> requestContext) {
    return accessor.getRequestServerPort(requestContext.request());
  }

  @Nullable
  @Override
  public String sockFamily(ServletRequestContext<REQUEST> requestContext) {
    return null;
  }

  @Override
  @Nullable
  public String sockPeerAddr(ServletRequestContext<REQUEST> requestContext) {
    return accessor.getRequestRemoteAddr(requestContext.request());
  }

  @Override
  @Nullable
  public Integer sockPeerPort(ServletRequestContext<REQUEST> requestContext) {
    return accessor.getRequestRemotePort(requestContext.request());
  }

  @Nullable
  @Override
  public String sockHostAddr(ServletRequestContext<REQUEST> requestContext) {
    return accessor.getRequestLocalAddr(requestContext.request());
  }

  @Nullable
  @Override
  public String sockHostName(ServletRequestContext<REQUEST> requestContext) {
    return accessor.getRequestLocalName(requestContext.request());
  }

  @Nullable
  @Override
  public Integer sockHostPort(ServletRequestContext<REQUEST> requestContext) {
    return accessor.getRequestLocalPort(requestContext.request());
  }
}
