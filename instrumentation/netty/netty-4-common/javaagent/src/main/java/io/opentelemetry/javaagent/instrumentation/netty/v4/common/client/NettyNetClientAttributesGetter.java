/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4.common.client;

import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetClientAttributesGetter;
import io.opentelemetry.javaagent.instrumentation.netty.v4.common.HttpRequestAndChannel;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;

final class NettyNetClientAttributesGetter
    extends InetSocketAddressNetClientAttributesGetter<HttpRequestAndChannel, HttpResponse> {

  @Override
  @Nullable
  public InetSocketAddress getAddress(
      HttpRequestAndChannel requestAndChannel, @Nullable HttpResponse response) {
    SocketAddress address = requestAndChannel.remoteAddress();
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
