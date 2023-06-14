/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesGetter;
import javax.annotation.Nullable;

public class ServletNetAttributesGetter<REQUEST, RESPONSE>
    implements NetServerAttributesGetter<
        ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>> {

  private final ServletAccessor<REQUEST, RESPONSE> accessor;

  public ServletNetAttributesGetter(ServletAccessor<REQUEST, RESPONSE> accessor) {
    this.accessor = accessor;
  }

  @Nullable
  @Override
  public String getNetworkProtocolName(
      ServletRequestContext<REQUEST> requestContext,
      @Nullable ServletResponseContext<RESPONSE> responseContext) {
    String protocol = accessor.getRequestProtocol(requestContext.request());
    if (protocol != null && protocol.startsWith("HTTP/")) {
      return "http";
    }
    return null;
  }

  @Nullable
  @Override
  public String getNetworkProtocolVersion(
      ServletRequestContext<REQUEST> requestContext,
      @Nullable ServletResponseContext<RESPONSE> responseContext) {
    String protocol = accessor.getRequestProtocol(requestContext.request());
    if (protocol != null && protocol.startsWith("HTTP/")) {
      return protocol.substring("HTTP/".length());
    }
    return null;
  }

  @Nullable
  @Override
  public String getServerAddress(ServletRequestContext<REQUEST> requestContext) {
    return accessor.getRequestServerName(requestContext.request());
  }

  @Nullable
  @Override
  public Integer getServerPort(ServletRequestContext<REQUEST> requestContext) {
    return accessor.getRequestServerPort(requestContext.request());
  }

  @Override
  @Nullable
  public String getClientSocketAddress(
      ServletRequestContext<REQUEST> requestContext,
      @Nullable ServletResponseContext<RESPONSE> response) {
    return accessor.getRequestRemoteAddr(requestContext.request());
  }

  @Override
  @Nullable
  public Integer getClientSocketPort(
      ServletRequestContext<REQUEST> requestContext,
      @Nullable ServletResponseContext<RESPONSE> response) {
    return accessor.getRequestRemotePort(requestContext.request());
  }

  @Nullable
  @Override
  public String getServerSocketAddress(
      ServletRequestContext<REQUEST> requestContext,
      @Nullable ServletResponseContext<RESPONSE> response) {
    return accessor.getRequestLocalAddr(requestContext.request());
  }

  @Nullable
  @Override
  public Integer getServerSocketPort(
      ServletRequestContext<REQUEST> requestContext,
      @Nullable ServletResponseContext<RESPONSE> response) {
    return accessor.getRequestLocalPort(requestContext.request());
  }
}
