/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.v2_0;

import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetClientAttributesGetter;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;
import org.asynchttpclient.Response;
import org.asynchttpclient.netty.request.NettyRequest;

final class AsyncHttpClientNetAttributesGetter
    extends InetSocketAddressNetClientAttributesGetter<RequestContext, Response> {

  @Nullable
  @Override
  public String getProtocolName(RequestContext request, @Nullable Response response) {
    HttpVersion httpVersion = getHttpVersion(request);
    if (httpVersion == null) {
      return null;
    }
    return httpVersion.protocolName();
  }

  @Nullable
  @Override
  public String getProtocolVersion(RequestContext request, @Nullable Response response) {
    HttpVersion httpVersion = getHttpVersion(request);
    if (httpVersion == null) {
      return null;
    }
    return httpVersion.majorVersion() + "." + httpVersion.minorVersion();
  }

  @Nullable
  private static HttpVersion getHttpVersion(RequestContext request) {
    NettyRequest nettyRequest = request.getNettyRequest();
    if (nettyRequest == null) {
      return null;
    }
    HttpRequest httpRequest = nettyRequest.getHttpRequest();
    if (httpRequest == null) {
      return null;
    }
    return httpRequest.getProtocolVersion();
  }

  @Nullable
  @Override
  public String getPeerName(RequestContext request) {
    return request.getRequest().getUri().getHost();
  }

  @Override
  public Integer getPeerPort(RequestContext request) {
    return request.getRequest().getUri().getPort();
  }

  @Override
  @Nullable
  protected InetSocketAddress getPeerSocketAddress(
      RequestContext request, @Nullable Response response) {
    if (response != null && response.getRemoteAddress() instanceof InetSocketAddress) {
      return (InetSocketAddress) response.getRemoteAddress();
    }
    return null;
  }
}
