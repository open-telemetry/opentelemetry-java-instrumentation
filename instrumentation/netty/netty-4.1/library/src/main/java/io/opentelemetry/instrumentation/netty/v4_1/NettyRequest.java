/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4_1;

import com.google.auto.value.AutoValue;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpRequest;
import java.net.SocketAddress;
import javax.annotation.Nullable;

/**
 * A wrapper for {@link io.opentelemetry.instrumentation.netty.common.v4_0.NettyRequest} that
 * provides a v4.1-specific API.
 */
@AutoValue
public abstract class NettyRequest {

  static NettyRequest create(HttpRequest request, Channel channel) {
    return new AutoValue_NettyRequest(
        io.opentelemetry.instrumentation.netty.common.v4_0.NettyRequest.create(request, channel));
  }

  /** Creates a v4.1 NettyRequest from a common NettyRequest. */
  public static NettyRequest create(
      io.opentelemetry.instrumentation.netty.common.v4_0.NettyRequest commonRequest) {
    return new AutoValue_NettyRequest(commonRequest);
  }

  abstract io.opentelemetry.instrumentation.netty.common.v4_0.NettyRequest delegate();

  /** Returns the {@link HttpRequest}. */
  public HttpRequest request() {
    return delegate().request();
  }

  /** Returns the {@link Channel}. */
  public Channel channel() {
    return delegate().channel();
  }

  /**
   * Return the {@link Channel#remoteAddress()} present when this {@link NettyRequest} was created.
   *
   * <p>We capture the remote address early because netty may return null when calling {@link
   * Channel#remoteAddress()} at the end of processing in cases of timeouts or other connection
   * issues.
   */
  @Nullable
  public SocketAddress remoteAddress() {
    return delegate().remoteAddress();
  }
}
