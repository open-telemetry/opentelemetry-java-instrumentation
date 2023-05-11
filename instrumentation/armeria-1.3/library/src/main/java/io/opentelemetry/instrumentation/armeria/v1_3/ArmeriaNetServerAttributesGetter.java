/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3;

import com.linecorp.armeria.common.RequestContext;
import com.linecorp.armeria.common.SessionProtocol;
import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetServerAttributesGetter;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;

final class ArmeriaNetServerAttributesGetter
    extends InetSocketAddressNetServerAttributesGetter<RequestContext> {

  @Override
  public String getProtocolName(RequestContext ctx) {
    return "http";
  }

  @Override
  public String getProtocolVersion(RequestContext ctx) {
    SessionProtocol protocol = ctx.sessionProtocol();
    return protocol.isMultiplex() ? "2.0" : "1.1";
  }

  @Nullable
  @Override
  public String getHostName(RequestContext ctx) {
    return null;
  }

  @Nullable
  @Override
  public Integer getHostPort(RequestContext ctx) {
    return null;
  }

  @Override
  @Nullable
  protected InetSocketAddress getPeerSocketAddress(RequestContext ctx) {
    SocketAddress address = ctx.remoteAddress();
    if (address instanceof InetSocketAddress) {
      return (InetSocketAddress) address;
    }
    return null;
  }

  @Nullable
  @Override
  protected InetSocketAddress getHostSocketAddress(RequestContext ctx) {
    SocketAddress address = ctx.localAddress();
    if (address instanceof InetSocketAddress) {
      return (InetSocketAddress) address;
    }
    return null;
  }
}
