/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.server;

import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetServerAttributesExtractor;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.HttpRequestAndChannel;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;
import org.jboss.netty.handler.codec.http.HttpResponse;

final class NettyNetServerAttributesExtractor
    extends InetSocketAddressNetServerAttributesExtractor<HttpRequestAndChannel, HttpResponse> {

  @Override
  @Nullable
  public String transport(HttpRequestAndChannel requestAndChannel) {
    return null;
  }

  @Override
  @Nullable
  public InetSocketAddress getAddress(HttpRequestAndChannel requestAndChannel) {
    SocketAddress address = requestAndChannel.channel().getRemoteAddress();
    if (address instanceof InetSocketAddress) {
      return (InetSocketAddress) address;
    }
    return null;
  }
}
