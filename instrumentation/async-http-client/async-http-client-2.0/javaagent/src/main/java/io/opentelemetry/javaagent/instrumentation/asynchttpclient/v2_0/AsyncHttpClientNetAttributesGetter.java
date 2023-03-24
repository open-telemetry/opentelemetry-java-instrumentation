/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.v2_0;

import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpVersion;
import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.InetSocketAddress;
import java.util.Optional;
import javax.annotation.Nullable;
import org.asynchttpclient.Response;
import org.asynchttpclient.netty.request.NettyRequest;

final class AsyncHttpClientNetAttributesGetter
    extends InetSocketAddressNetClientAttributesGetter<RequestContext, Response> {

  @Override
  public String getTransport(RequestContext request, @Nullable Response response) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Nullable
  @Override
  public String getProtocolName(RequestContext request, @Nullable Response response) {
    return Optional.of(request)
        .map(RequestContext::getNettyRequest)
        .map(NettyRequest::getHttpRequest)
        .map(HttpMessage::getProtocolVersion)
        .map(HttpVersion::protocolName)
        .orElse(null);
  }

  @Nullable
  @Override
  public String getProtocolVersion(RequestContext request, @Nullable Response response) {
    return Optional.of(request)
        .map(RequestContext::getNettyRequest)
        .map(NettyRequest::getHttpRequest)
        .map(HttpMessage::getProtocolVersion)
        .map(p -> p.majorVersion() + "." + p.minorVersion())
        .orElse(null);
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
