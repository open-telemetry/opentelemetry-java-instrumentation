/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.common.v4_0.internal.server;

import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpVersion;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesGetter;
import io.opentelemetry.instrumentation.netty.common.v4_0.internal.ChannelUtil;
import io.opentelemetry.instrumentation.netty.common.v4_0.internal.HttpSchemeUtil;
import io.opentelemetry.instrumentation.netty.common.v4_0.internal.NettyCommonRequest;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class NettyHttpServerAttributesGetter
    implements HttpServerAttributesGetter<NettyCommonRequest, HttpResponse> {

  @Override
  public String getHttpRequestMethod(NettyCommonRequest requestAndChannel) {
    return requestAndChannel.getRequest().getMethod().name();
  }

  @Override
  public List<String> getHttpRequestHeader(NettyCommonRequest requestAndChannel, String name) {
    return requestAndChannel.getRequest().headers().getAll(name);
  }

  @Override
  public Integer getHttpResponseStatusCode(
      NettyCommonRequest requestAndChannel, HttpResponse response, @Nullable Throwable error) {
    return response.getStatus().code();
  }

  @Override
  public List<String> getHttpResponseHeader(
      NettyCommonRequest requestAndChannel, HttpResponse response, String name) {
    return response.headers().getAll(name);
  }

  @Override
  public String getUrlScheme(NettyCommonRequest requestAndChannel) {
    return HttpSchemeUtil.getScheme(requestAndChannel);
  }

  @Override
  public String getUrlPath(NettyCommonRequest requestAndChannel) {
    String fullPath = requestAndChannel.getRequest().getUri();
    int separatorPos = fullPath.indexOf('?');
    return separatorPos == -1 ? fullPath : fullPath.substring(0, separatorPos);
  }

  @Override
  public String getUrlQuery(NettyCommonRequest requestAndChannel) {
    String fullPath = requestAndChannel.getRequest().getUri();
    int separatorPos = fullPath.indexOf('?');
    return separatorPos == -1 ? null : fullPath.substring(separatorPos + 1);
  }

  @Override
  public String getNetworkTransport(NettyCommonRequest requestAndChannel, HttpResponse response) {
    return ChannelUtil.getNetworkTransport(requestAndChannel.getChannel());
  }

  @Override
  public String getNetworkProtocolName(
      NettyCommonRequest requestAndChannel, @Nullable HttpResponse response) {
    return requestAndChannel.getRequest().getProtocolVersion().protocolName();
  }

  @Override
  public String getNetworkProtocolVersion(
      NettyCommonRequest requestAndChannel, @Nullable HttpResponse response) {
    HttpVersion version = requestAndChannel.getRequest().getProtocolVersion();
    if (version.minorVersion() == 0) {
      return Integer.toString(version.majorVersion());
    }
    return version.majorVersion() + "." + version.minorVersion();
  }

  @Override
  @Nullable
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      NettyCommonRequest requestAndChannel, @Nullable HttpResponse response) {
    SocketAddress address = requestAndChannel.getRemoteAddress();
    if (address instanceof InetSocketAddress) {
      return (InetSocketAddress) address;
    }
    return null;
  }

  @Nullable
  @Override
  public InetSocketAddress getNetworkLocalInetSocketAddress(
      NettyCommonRequest requestAndChannel, @Nullable HttpResponse response) {
    SocketAddress address = requestAndChannel.getChannel().localAddress();
    if (address instanceof InetSocketAddress) {
      return (InetSocketAddress) address;
    }
    return null;
  }
}
