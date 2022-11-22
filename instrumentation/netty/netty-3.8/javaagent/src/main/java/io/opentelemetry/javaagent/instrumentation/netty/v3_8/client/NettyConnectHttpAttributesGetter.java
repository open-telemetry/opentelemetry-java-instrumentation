/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.client;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import io.opentelemetry.instrumentation.netty.common.internal.NettyConnectionRequest;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import org.jboss.netty.channel.Channel;

enum NettyConnectHttpAttributesGetter
    implements HttpClientAttributesGetter<NettyConnectionRequest, Channel> {
  INSTANCE;

  @Nullable
  @Override
  public String url(NettyConnectionRequest nettyConnectionRequest) {
    return null;
  }

  @Nullable
  @Override
  public String flavor(NettyConnectionRequest nettyConnectionRequest, @Nullable Channel channel) {
    return null;
  }

  @Nullable
  @Override
  public String method(NettyConnectionRequest nettyConnectionRequest) {
    return null;
  }

  @Override
  public List<String> requestHeader(NettyConnectionRequest nettyConnectionRequest, String name) {
    return Collections.emptyList();
  }

  @Nullable
  @Override
  public Integer statusCode(
      NettyConnectionRequest nettyConnectionRequest, Channel channel, @Nullable Throwable error) {
    return null;
  }

  @Override
  public List<String> responseHeader(
      NettyConnectionRequest nettyConnectionRequest, Channel channel, String name) {
    return Collections.emptyList();
  }
}
