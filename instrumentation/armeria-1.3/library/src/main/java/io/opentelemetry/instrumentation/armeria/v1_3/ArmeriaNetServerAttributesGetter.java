/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3;

import com.linecorp.armeria.common.RequestContext;
import com.linecorp.armeria.common.SessionProtocol;
import com.linecorp.armeria.common.logging.RequestLog;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesGetter;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;

final class ArmeriaNetServerAttributesGetter
    implements NetServerAttributesGetter<RequestContext, RequestLog> {

  @Override
  public String getNetworkProtocolName(RequestContext ctx, @Nullable RequestLog requestLog) {
    return "http";
  }

  @Override
  public String getNetworkProtocolVersion(RequestContext ctx, @Nullable RequestLog requestLog) {
    SessionProtocol protocol = ctx.sessionProtocol();
    return protocol.isMultiplex() ? "2.0" : "1.1";
  }

  @Nullable
  @Override
  public String getServerAddress(RequestContext ctx) {
    return null;
  }

  @Nullable
  @Override
  public Integer getServerPort(RequestContext ctx) {
    return null;
  }

  @Override
  @Nullable
  public InetSocketAddress getClientInetSocketAddress(
      RequestContext ctx, @Nullable RequestLog requestLog) {
    SocketAddress address = ctx.remoteAddress();
    if (address instanceof InetSocketAddress) {
      return (InetSocketAddress) address;
    }
    return null;
  }

  @Nullable
  @Override
  public InetSocketAddress getServerInetSocketAddress(
      RequestContext ctx, @Nullable RequestLog log) {
    SocketAddress address = ctx.localAddress();
    if (address instanceof InetSocketAddress) {
      return (InetSocketAddress) address;
    }
    return null;
  }
}
