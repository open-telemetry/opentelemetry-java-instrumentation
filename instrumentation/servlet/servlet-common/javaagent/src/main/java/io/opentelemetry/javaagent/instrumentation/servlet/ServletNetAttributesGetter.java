/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesGetter;
import javax.annotation.Nullable;

public class ServletNetAttributesGetter<REQUEST, RESPONSE>
    implements NetServerAttributesGetter<ServletRequestContext<REQUEST>> {

  private final ServletAccessor<REQUEST, RESPONSE> accessor;

  public ServletNetAttributesGetter(ServletAccessor<REQUEST, RESPONSE> accessor) {
    this.accessor = accessor;
  }

  @Nullable
  @Override
  public String getProtocolName(ServletRequestContext<REQUEST> requestContext) {
    String protocol = accessor.getRequestProtocol(requestContext.request());
    if (protocol != null && protocol.startsWith("HTTP/")) {
      return "http";
    }
    return null;
  }

  @Nullable
  @Override
  public String getProtocolVersion(ServletRequestContext<REQUEST> requestContext) {
    String protocol = accessor.getRequestProtocol(requestContext.request());
    if (protocol != null && protocol.startsWith("HTTP/")) {
      return protocol.substring("HTTP/".length());
    }
    return null;
  }

  @Nullable
  @Override
  public String getHostName(ServletRequestContext<REQUEST> requestContext) {
    return accessor.getRequestServerName(requestContext.request());
  }

  @Nullable
  @Override
  public Integer getHostPort(ServletRequestContext<REQUEST> requestContext) {
    return accessor.getRequestServerPort(requestContext.request());
  }

  @Override
  @Nullable
  public String getSockPeerAddr(ServletRequestContext<REQUEST> requestContext) {
    return accessor.getRequestRemoteAddr(requestContext.request());
  }

  @Override
  @Nullable
  public Integer getSockPeerPort(ServletRequestContext<REQUEST> requestContext) {
    return accessor.getRequestRemotePort(requestContext.request());
  }

  @Nullable
  @Override
  public String getSockHostAddr(ServletRequestContext<REQUEST> requestContext) {
    return accessor.getRequestLocalAddr(requestContext.request());
  }

  @Nullable
  @Override
  public Integer getSockHostPort(ServletRequestContext<REQUEST> requestContext) {
    return accessor.getRequestLocalPort(requestContext.request());
  }
}
