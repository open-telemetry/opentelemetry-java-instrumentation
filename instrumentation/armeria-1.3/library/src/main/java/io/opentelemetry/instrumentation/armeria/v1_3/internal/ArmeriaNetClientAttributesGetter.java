/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3.internal;

import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.RequestContext;
import com.linecorp.armeria.common.SessionProtocol;
import com.linecorp.armeria.common.logging.RequestLog;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ArmeriaNetClientAttributesGetter
    implements NetClientAttributesGetter<RequestContext, RequestLog> {

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
    HttpRequest request = request(ctx);
    String authority = request.authority();
    if (authority == null) {
      return null;
    }
    int separatorPos = authority.indexOf(':');
    return separatorPos == -1 ? authority : authority.substring(0, separatorPos);
  }

  @Nullable
  @Override
  public Integer getServerPort(RequestContext ctx) {
    HttpRequest request = request(ctx);
    String authority = request.authority();
    if (authority == null) {
      return null;
    }
    int separatorPos = authority.indexOf(':');
    if (separatorPos == -1) {
      return null;
    }
    try {
      return Integer.parseInt(authority.substring(separatorPos + 1));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  @Override
  @Nullable
  public InetSocketAddress getServerInetSocketAddress(
      RequestContext ctx, @Nullable RequestLog requestLog) {
    return RequestContextAccess.remoteAddress(ctx);
  }

  private static HttpRequest request(RequestContext ctx) {
    HttpRequest request = ctx.request();
    if (request == null) {
      throw new IllegalStateException(
          "Context always has a request in decorators, this exception indicates a programming bug.");
    }
    return request;
  }
}
