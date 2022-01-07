/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.client;

import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetClientAttributesAdapter;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.HttpRequestAndChannel;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;
import org.jboss.netty.handler.codec.http.HttpResponse;

final class NettyNetClientAttributesAdapter
    extends InetSocketAddressNetClientAttributesAdapter<HttpRequestAndChannel, HttpResponse> {

  @Override
  @Nullable
  public InetSocketAddress getAddress(
      HttpRequestAndChannel requestAndChannel, @Nullable HttpResponse response) {
    SocketAddress address = requestAndChannel.channel().getRemoteAddress();
    if (address instanceof InetSocketAddress) {
      return (InetSocketAddress) address;
    }
    return null;
  }

  @Override
  @Nullable
  public String transport(
      HttpRequestAndChannel requestAndChannel, @Nullable HttpResponse response) {
    return null;
  }
}
