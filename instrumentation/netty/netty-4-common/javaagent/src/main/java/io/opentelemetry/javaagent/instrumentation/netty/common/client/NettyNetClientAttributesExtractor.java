/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.common.client;

import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetClientAttributesExtractor;
import io.opentelemetry.javaagent.instrumentation.netty.common.HttpRequestAndChannel;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import org.checkerframework.checker.nullness.qual.Nullable;

final class NettyNetClientAttributesExtractor
    extends InetSocketAddressNetClientAttributesExtractor<HttpRequestAndChannel, HttpResponse> {

  @Override
  public @Nullable InetSocketAddress getAddress(
      HttpRequestAndChannel requestAndChannel, @Nullable HttpResponse response) {
    SocketAddress address = requestAndChannel.channel().remoteAddress();
    if (address instanceof InetSocketAddress) {
      return (InetSocketAddress) address;
    }
    return null;
  }

  @Override
  public @Nullable String transport(
      HttpRequestAndChannel requestAndChannel, @Nullable HttpResponse response) {
    return null;
  }
}
