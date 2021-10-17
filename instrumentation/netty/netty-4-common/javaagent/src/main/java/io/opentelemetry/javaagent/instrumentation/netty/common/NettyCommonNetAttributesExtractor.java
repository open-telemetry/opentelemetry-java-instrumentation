/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.common;

import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetServerAttributesExtractor;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;

public final class NettyCommonNetAttributesExtractor
    extends InetSocketAddressNetServerAttributesExtractor<HttpRequestAndChannel, HttpResponse> {

  @Override
  @Nullable
  public String transport(HttpRequestAndChannel requestAndChannel) {
    return null;
  }

  @Override
  @Nullable
  public InetSocketAddress getAddress(HttpRequestAndChannel requestAndChannel) {
    SocketAddress address = requestAndChannel.channel().remoteAddress();
    if (address instanceof InetSocketAddress) {
      return (InetSocketAddress) address;
    }
    return null;
  }
}
