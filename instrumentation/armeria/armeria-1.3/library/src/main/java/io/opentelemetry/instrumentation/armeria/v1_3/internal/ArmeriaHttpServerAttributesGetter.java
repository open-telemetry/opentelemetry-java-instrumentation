/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3.internal;

import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.SessionProtocol;
import com.linecorp.armeria.common.logging.RequestLog;
import com.linecorp.armeria.server.ServiceRequestContext;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesGetter;
import java.net.InetSocketAddress;
import java.util.List;
import javax.annotation.Nullable;

enum ArmeriaHttpServerAttributesGetter
    implements HttpServerAttributesGetter<ServiceRequestContext, RequestLog> {
  INSTANCE;

  @Override
  public String getHttpRequestMethod(ServiceRequestContext ctx) {
    return ctx.method().name();
  }

  @Override
  @Nullable
  public String getUrlScheme(ServiceRequestContext ctx) {
    return request(ctx).scheme();
  }

  @Override
  public String getUrlPath(ServiceRequestContext ctx) {
    String fullPath = request(ctx).path();
    int separatorPos = fullPath.indexOf('?');
    return separatorPos == -1 ? fullPath : fullPath.substring(0, separatorPos);
  }

  @Nullable
  @Override
  public String getUrlQuery(ServiceRequestContext ctx) {
    String fullPath = request(ctx).path();
    int separatorPos = fullPath.indexOf('?');
    return separatorPos == -1 ? null : fullPath.substring(separatorPos + 1);
  }

  @Override
  public List<String> getHttpRequestHeader(ServiceRequestContext ctx, String name) {
    return request(ctx).headers().getAll(name);
  }

  @Override
  @Nullable
  public Integer getHttpResponseStatusCode(
      ServiceRequestContext ctx, RequestLog requestLog, @Nullable Throwable error) {
    HttpStatus status = requestLog.responseHeaders().status();
    if (!status.equals(HttpStatus.UNKNOWN)) {
      return status.code();
    }
    return null;
  }

  @Override
  public List<String> getHttpResponseHeader(
      ServiceRequestContext ctx, RequestLog requestLog, String name) {
    return requestLog.responseHeaders().getAll(name);
  }

  @Override
  @Nullable
  public String getHttpRoute(ServiceRequestContext ctx) {
    return ctx.config().route().patternString();
  }

  @Override
  public String getNetworkProtocolName(ServiceRequestContext ctx, @Nullable RequestLog requestLog) {
    return "http";
  }

  @Override
  public String getNetworkProtocolVersion(
      ServiceRequestContext ctx, @Nullable RequestLog requestLog) {
    SessionProtocol protocol = ctx.sessionProtocol();
    return protocol.isMultiplex() ? "2" : "1.1";
  }

  @Override
  @Nullable
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      ServiceRequestContext ctx, @Nullable RequestLog requestLog) {
    return RequestContextAccess.remoteAddress(ctx);
  }

  @Nullable
  @Override
  public InetSocketAddress getNetworkLocalInetSocketAddress(
      ServiceRequestContext ctx, @Nullable RequestLog log) {
    return RequestContextAccess.localAddress(ctx);
  }

  private static HttpRequest request(ServiceRequestContext ctx) {
    HttpRequest request = ctx.request();
    if (request == null) {
      throw new IllegalStateException(
          "Context always has a request in decorators, this exception indicates a programming bug.");
    }
    return request;
  }
}
