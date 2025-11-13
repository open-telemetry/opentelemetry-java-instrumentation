/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.common.v4_0.internal.client;

import static io.opentelemetry.instrumentation.netty.common.v4_0.internal.HttpSchemeUtil.getScheme;

import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpVersion;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesGetter;
import io.opentelemetry.instrumentation.netty.common.v4_0.NettyRequest;
import io.opentelemetry.instrumentation.netty.common.v4_0.internal.ChannelUtil;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import javax.annotation.Nullable;

final class NettyHttpClientAttributesGetter
    implements HttpClientAttributesGetter<NettyRequest, HttpResponse> {

  @Override
  @Nullable
  public String getUrlFull(NettyRequest requestAndChannel) {
    try {
      String hostHeader = getHost(requestAndChannel);
      String target = requestAndChannel.request().getUri();
      URI uri = new URI(target);
      if ((uri.getHost() == null || uri.getHost().equals("")) && hostHeader != null) {
        return getScheme(requestAndChannel) + "://" + hostHeader + target;
      }
      return uri.toString();
    } catch (URISyntaxException e) {
      return null;
    }
  }

  private String getHost(NettyRequest requestAndChannel) {
    List<String> values = getHttpRequestHeader(requestAndChannel, "host");
    return values.isEmpty() ? null : values.get(0);
  }

  @Override
  public String getHttpRequestMethod(NettyRequest requestAndChannel) {
    return requestAndChannel.request().getMethod().name();
  }

  @Override
  public List<String> getHttpRequestHeader(NettyRequest requestAndChannel, String name) {
    return requestAndChannel.request().headers().getAll(name);
  }

  @Override
  public Integer getHttpResponseStatusCode(
      NettyRequest requestAndChannel, HttpResponse response, @Nullable Throwable error) {
    return response.getStatus().code();
  }

  @Override
  public List<String> getHttpResponseHeader(
      NettyRequest requestAndChannel, HttpResponse response, String name) {
    return response.headers().getAll(name);
  }

  @Override
  public String getNetworkTransport(
      NettyRequest requestAndChannel, @Nullable HttpResponse response) {
    return ChannelUtil.getNetworkTransport(requestAndChannel.channel());
  }

  @Override
  public String getNetworkProtocolName(
      NettyRequest requestAndChannel, @Nullable HttpResponse response) {
    return requestAndChannel.request().getProtocolVersion().protocolName();
  }

  @Override
  public String getNetworkProtocolVersion(
      NettyRequest requestAndChannel, @Nullable HttpResponse response) {
    HttpVersion version = requestAndChannel.request().getProtocolVersion();
    if (version.minorVersion() == 0) {
      return Integer.toString(version.majorVersion());
    }
    return version.majorVersion() + "." + version.minorVersion();
  }

  @Nullable
  @Override
  public String getServerAddress(NettyRequest requestAndChannel) {
    return null;
  }

  @Nullable
  @Override
  public Integer getServerPort(NettyRequest requestAndChannel) {
    return null;
  }

  @Override
  @Nullable
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      NettyRequest requestAndChannel, @Nullable HttpResponse response) {
    SocketAddress address = requestAndChannel.remoteAddress();
    if (address instanceof InetSocketAddress) {
      return (InetSocketAddress) address;
    }
    return null;
  }
}
