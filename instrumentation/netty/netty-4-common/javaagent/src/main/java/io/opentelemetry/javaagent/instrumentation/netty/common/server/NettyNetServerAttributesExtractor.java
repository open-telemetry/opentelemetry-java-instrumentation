/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.common.server;

import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetServerAttributesExtractor;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import org.checkerframework.checker.nullness.qual.Nullable;

final class NettyNetServerAttributesExtractor
    extends InetSocketAddressNetServerAttributesExtractor<HttpRequestAndChannel, HttpResponse> {

  @Override
  public @Nullable String transport(HttpRequestAndChannel requestAndChannel) {
    return null;
  }

  @Override
  public @Nullable InetSocketAddress getAddress(HttpRequestAndChannel requestAndChannel) {
    SocketAddress address = requestAndChannel.channel().remoteAddress();
    if (address instanceof InetSocketAddress) {
      return (InetSocketAddress) address;
    }
    return null;
  }
}
